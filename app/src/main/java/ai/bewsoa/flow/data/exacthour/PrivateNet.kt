package ai.bewsoa.flow.data.exacthour

/**
 * The gate that keeps the app's one cleartext door pointed at the user's own
 * network.
 *
 * `res/xml/network_security_config.xml` allows cleartext at the base config
 * because Android's network-security-config has no CIDR syntax — there is no
 * way to write "private ranges only" in that file. So the rule lives here
 * instead, [ExactHourClient] runs every request through it, and it is plain
 * enough to unit test.
 *
 * Pure, no Android imports, no DNS lookups: a hostname is judged on what it
 * says, never on what it resolves to.
 */
object PrivateNet {

    /** RFC 1918, link-local, and loopback — plus mDNS names, which are LAN by definition. */
    fun isPrivate(host: String): Boolean {
        val clean = host.trim().trim('[', ']').lowercase()
        if (clean.isEmpty()) return false
        if (clean.endsWith(".local") && clean.length > ".local".length) return true

        val octets = clean.split(".")
        if (octets.size != 4) return false
        val parts = octets.map { it.toIntOrNull() ?: return false }
        if (parts.any { it !in 0..255 }) return false

        val (a, b, _, _) = parts
        return when {
            a == 10 -> true                     // 10.0.0.0/8
            a == 127 -> true                    // 127.0.0.0/8 — the loopback demo server
            a == 172 && b in 16..31 -> true     // 172.16.0.0/12
            a == 192 && b == 168 -> true        // 192.168.0.0/16
            a == 169 && b == 254 -> true        // 169.254.0.0/16 link-local
            else -> false
        }
    }

    /** Throws [ExactHourError.NotPrivate] rather than letting a public host see cleartext. */
    fun require(host: String) {
        if (!isPrivate(host)) throw ExactHourError.NotPrivate(host)
    }
}
