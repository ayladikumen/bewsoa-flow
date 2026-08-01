package ai.bewsoa.flow.data.exacthour

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app allows cleartext at the base config because Android's
 * network-security-config has no CIDR syntax — "private ranges only" is not
 * expressible there. This class is where that rule actually lives, so it is
 * the one that has to be right.
 */
class PrivateNetTest {

    @Test
    fun `the private ranges are allowed`() {
        assertTrue(PrivateNet.isPrivate("192.168.1.50"))
        assertTrue(PrivateNet.isPrivate("10.4.4.4"))
        assertTrue(PrivateNet.isPrivate("172.16.0.1"))
        assertTrue(PrivateNet.isPrivate("172.31.255.254"))
        assertTrue(PrivateNet.isPrivate("169.254.1.1"))
        // The hardware-free demo server runs on loopback.
        assertTrue(PrivateNet.isPrivate("127.0.0.1"))
    }

    @Test
    fun `mDNS names are LAN by definition`() {
        assertTrue(PrivateNet.isPrivate("exacthour.local"))
        assertTrue(PrivateNet.isPrivate("EXACTHOUR.LOCAL"))
        assertFalse(PrivateNet.isPrivate(".local"))
    }

    @Test
    fun `public addresses are refused`() {
        assertFalse(PrivateNet.isPrivate("8.8.8.8"))
        assertFalse(PrivateNet.isPrivate("1.1.1.1"))
        // Just outside 172.16/12 on both sides — the range people get wrong.
        assertFalse(PrivateNet.isPrivate("172.15.0.1"))
        assertFalse(PrivateNet.isPrivate("172.32.0.1"))
    }

    @Test
    fun `hostnames that aren't mDNS are refused`() {
        assertFalse(PrivateNet.isPrivate("evil.example.com"))
        assertFalse(PrivateNet.isPrivate("localhost"))
    }

    @Test
    fun `junk is refused rather than guessed at`() {
        assertFalse(PrivateNet.isPrivate(""))
        assertFalse(PrivateNet.isPrivate("   "))
        assertFalse(PrivateNet.isPrivate("192.168.1"))
        assertFalse(PrivateNet.isPrivate("192.168.1.999"))
        assertFalse(PrivateNet.isPrivate("192.168.1.x"))
    }

    @Test
    fun `require throws for anything off the LAN`() {
        PrivateNet.require("192.168.1.50")
        assertThrows(ExactHourError.NotPrivate::class.java) {
            PrivateNet.require("8.8.8.8")
        }
    }
}
