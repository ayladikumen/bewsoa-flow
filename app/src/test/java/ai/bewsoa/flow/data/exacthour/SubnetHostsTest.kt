package ai.bewsoa.flow.data.exacthour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The address list "Find my clock" knocks on. */
class SubnetHostsTest {

    @Test
    fun `every host in the subnet except the phone itself`() {
        val hosts = ExactHourDiscovery.hostsForSubnet("192.168.1.42")
        assertEquals(253, hosts.size)
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
        assertFalse("must not probe itself", hosts.contains("192.168.1.42"))
        assertFalse("network address", hosts.contains("192.168.1.0"))
        assertFalse("broadcast address", hosts.contains("192.168.1.255"))
    }

    @Test
    fun `the sweep is ordered, so progress counts up predictably`() {
        val hosts = ExactHourDiscovery.hostsForSubnet("10.0.0.5")
        assertEquals("10.0.0.1", hosts.first())
        assertEquals("10.0.0.254", hosts.last())
    }

    @Test
    fun `a phone on the first or last address still gets a full sweep`() {
        assertEquals(253, ExactHourDiscovery.hostsForSubnet("192.168.1.1").size)
        assertEquals(253, ExactHourDiscovery.hostsForSubnet("192.168.1.254").size)
        // Outside the swept range: nothing is excluded, so all 254 remain.
        assertEquals(254, ExactHourDiscovery.hostsForSubnet("192.168.1.255").size)
    }

    @Test
    fun `a malformed address sweeps nothing rather than guessing`() {
        assertTrue(ExactHourDiscovery.hostsForSubnet("").isEmpty())
        assertTrue(ExactHourDiscovery.hostsForSubnet("192.168.1").isEmpty())
        assertTrue(ExactHourDiscovery.hostsForSubnet("fe80::1").isEmpty())
    }
}
