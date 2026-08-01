package ai.bewsoa.flow.data.exacthour

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** A clock that answered a probe. */
data class DiscoveredClock(
    val host: String,
    val port: Int,
    val name: String,
    val display: String
) {
    fun toEndpoint(): ClockEndpoint = ClockEndpoint(host, port, name)
}

/**
 * "Find my clock" — the server advertises nothing over mDNS, so the only way to
 * locate it without asking the user for an IP is to knock on every door in the
 * subnet and see which one answers like a clock.
 */
object ExactHourDiscovery {

    /**
     * Sweeps the phone's own /24. [onProgress] reports (probed, total) so the
     * settings card can show a live count.
     *
     * Ordering: the mDNS name first (one probe, free when it works), then the
     * standard port across the subnet, and only if that finds nothing, the
     * ports `dev/demo_server.py` uses — so someone testing against the
     * simulator isn't left staring at an empty list.
     */
    suspend fun scan(
        context: Context,
        onProgress: (probed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<DiscoveredClock>> {
        val selfIp = localIpv4(context)
            ?: return Result.failure(ExactHourError.OffNetwork)
        val hosts = hostsForSubnet(selfIp)

        val viaMdns = probe(MDNS_HOST, ClockLimits.DEFAULT_PORT)
        if (viaMdns != null) return Result.success(listOf(viaMdns))

        val onDefaultPort = sweep(hosts, ClockLimits.DEFAULT_PORT, onProgress)
        if (onDefaultPort.isNotEmpty()) return Result.success(onDefaultPort)

        // Nothing on 8080. The hardware-free demo server lands on 8731 and
        // walks forward when the port is busy, so try its whole chain.
        for (port in ClockLimits.DEMO_PORTS) {
            val found = sweep(hosts, port, onProgress)
            if (found.isNotEmpty()) return Result.success(found)
        }
        return Result.success(emptyList())
    }

    private suspend fun sweep(
        hosts: List<String>,
        port: Int,
        onProgress: (Int, Int) -> Unit
    ): List<DiscoveredClock> = withContext(Dispatchers.IO) {
        // 24 coroutines report into this, so it can't be a plain Int.
        val probed = AtomicInteger(0)
        val gate = Semaphore(CONCURRENCY)
        coroutineScope {
            hosts.map { host ->
                async {
                    gate.withPermit {
                        coroutineContext.ensureActive()
                        probe(host, port).also {
                            onProgress(probed.incrementAndGet(), hosts.size)
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * One knock. Returns null for everything that isn't a clock — including
     * hosts that answer JSON but aren't this device, which a home LAN has
     * plenty of.
     *
     * Note [ExactHourClient] doesn't observe cancellation mid-socket, so a
     * cancelled scan can leave a few probes running to their 400 ms read
     * timeout. Bounded and harmless.
     */
    private suspend fun probe(host: String, port: Int): DiscoveredClock? {
        if (!PrivateNet.isPrivate(host)) return null
        val status = ExactHourClient(ClockEndpoint(host, port)).probe().getOrNull() ?: return null
        return DiscoveredClock(
            host = host,
            port = port,
            name = status.name,
            display = status.display
        )
    }

    /**
     * Every address in the phone's /24 except the network address, the
     * broadcast address, and the phone itself.
     *
     * Always a /24 whatever prefix the link reports: a /16 is 65 000 probes,
     * which is never a reasonable thing to do to a phone battery. Wider LANs
     * are what the manual IP field is for.
     */
    fun hostsForSubnet(selfIp: String): List<String> {
        val parts = selfIp.trim().split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        val self = parts[3].toInt()
        return (1..254).filter { it != self }.map { "$prefix.$it" }
    }

    /**
     * The phone's own IPv4, via ConnectivityManager rather than WifiManager —
     * the latter needs location permission for anything useful. Null when
     * we're not on a network the clock could possibly be on.
     */
    private fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local) return null
        return cm.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    /**
     * Avahi on the Pi advertises its hostname for ssh, so this sometimes
     * resolves. Best-effort only — Android's `.local` resolution is
     * inconsistent across OEMs, which is why it is never stored as a default.
     */
    private const val MDNS_HOST = "exacthour.local"

    /** 24 sockets at a time: ~11 waves over a /24, comfortably inside what a phone will open. */
    private const val CONCURRENCY = 24
}
