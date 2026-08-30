package com.motadata.ipam.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IPv4Util bitwise calculation engine.
 */
class IPv4UtilTest {

    @Test
    void testIpv4Validation() {

        assertTrue(IPv4Util.isValidIpv4("192.168.1.1"));

        assertTrue(IPv4Util.isValidIpv4("10.0.0.1"));

        assertTrue(IPv4Util.isValidIpv4("0.0.0.0"));

        assertTrue(IPv4Util.isValidIpv4("255.255.255.255"));

        assertFalse(IPv4Util.isValidIpv4("256.0.0.1"));

        assertFalse(IPv4Util.isValidIpv4("192.168.1"));

        assertFalse(IPv4Util.isValidIpv4("192.168.1.1.1"));

        assertFalse(IPv4Util.isValidIpv4("invalid"));

        assertFalse(IPv4Util.isValidIpv4(null));

        assertFalse(IPv4Util.isValidIpv4(""));

    }

    @Test
    void testIpConversionRoundtrip() {

        String testIp = "192.168.1.1";

        long ipNum = IPv4Util.ipToLong(testIp);

        assertEquals(testIp, IPv4Util.longToIp(ipNum));

        // Test boundary values
        assertEquals("0.0.0.0", IPv4Util.longToIp(IPv4Util.ipToLong("0.0.0.0")));

        assertEquals("255.255.255.255", IPv4Util.longToIp(IPv4Util.ipToLong("255.255.255.255")));

    }

    @Test
    void testSubnetMaskAndPrefixCalculation() {

        assertEquals("255.255.255.0", IPv4Util.prefixToMaskString(24));

        assertEquals("255.255.0.0", IPv4Util.prefixToMaskString(16));

        assertEquals("255.0.0.0", IPv4Util.prefixToMaskString(8));

        assertEquals("255.255.255.252", IPv4Util.prefixToMaskString(30));

        assertEquals("0.0.0.0", IPv4Util.prefixToMaskString(0));

        assertEquals("255.255.255.255", IPv4Util.prefixToMaskString(32));

    }

    @Test
    void testNetworkAndBroadcastAddress() {

        long ip = IPv4Util.ipToLong("192.168.1.45");

        long network = IPv4Util.getNetworkAddress(ip, 24);

        long broadcast = IPv4Util.getBroadcastAddress(network, 24);

        assertEquals("192.168.1.0", IPv4Util.longToIp(network));

        assertEquals("192.168.1.255", IPv4Util.longToIp(broadcast));

    }

    @Test
    void testUsableHostCount() {

        assertEquals(254L, IPv4Util.getUsableHostCount(24));

        assertEquals(65534L, IPv4Util.getUsableHostCount(16));

        assertEquals(2L, IPv4Util.getUsableHostCount(30));

        assertEquals(0L, IPv4Util.getUsableHostCount(31));

        assertEquals(0L, IPv4Util.getUsableHostCount(32));

    }

    @Test
    void testSubnetOverlapDetection() {

        // Range A: 192.168.1.0 to 192.168.1.255
        long startA = IPv4Util.ipToLong("192.168.1.0");

        long endA = IPv4Util.ipToLong("192.168.1.255");

        // Range B (Overlapping /16): 192.168.0.0 to 192.168.255.255
        long startB = IPv4Util.ipToLong("192.168.0.0");

        long endB = IPv4Util.ipToLong("192.168.255.255");

        assertTrue(IPv4Util.isOverlapping(startA, endA, startB, endB));

        // Range C (Non-overlapping): 10.0.0.0 to 10.0.255.255
        long startC = IPv4Util.ipToLong("10.0.0.0");

        long endC = IPv4Util.ipToLong("10.0.255.255");

        assertFalse(IPv4Util.isOverlapping(startA, endA, startC, endC));

    }

    @Test
    void testIpChunkingGenerator() {

        long network = IPv4Util.ipToLong("192.168.1.0");

        long broadcast = IPv4Util.ipToLong("192.168.1.255");

        // Total usable IPs = 254. With chunk size 100 -> 3 chunks (100, 100, 54)
        List<List<String>> chunks = IPv4Util.generateIpChunks(network, broadcast, 100);

        assertEquals(3, chunks.size());

        assertEquals(100, chunks.get(0).size());

        assertEquals(100, chunks.get(1).size());

        assertEquals(54, chunks.get(2).size());

        assertEquals("192.168.1.1", chunks.get(0).get(0));

        assertEquals("192.168.1.254", chunks.get(2).get(53));

    }

    @Test
    void testGenerateIpRange() {

        long start = IPv4Util.ipToLong("10.0.0.1");

        long end = IPv4Util.ipToLong("10.0.0.5");

        List<String> range = IPv4Util.generateIpRange(start, end);

        assertEquals(5, range.size());

        assertEquals("10.0.0.1", range.get(0));

        assertEquals("10.0.0.5", range.get(4));

        assertTrue(IPv4Util.generateIpRange(end, start).isEmpty());

    }

}
