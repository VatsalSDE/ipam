package com.motadata.ipam.util;

import java.util.ArrayList;

import java.util.List;

import java.util.regex.Pattern;

/**
 * High-performance O(1) Bitwise IPv4 & CIDR Calculation Engine.
 * Converts IPv4 addresses to 32-bit unsigned integers, calculates netmasks,
 * network/broadcast addresses, usable host counts, detects subnet overlaps,
 * and generates bounded IP chunks for memory-safe database operations.
 */
public final class IPv4Util {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private IPv4Util() {

        // Utility class - prevent instantiation

    }

    /**
     * Validates if a string is a well-formed IPv4 address in dot-decimal notation.
     */
    public static boolean isValidIpv4(String ip) {

        if (ip == null || ip.isBlank()) {

            return false;

        }

        return IPV4_PATTERN.matcher(ip.trim()).matches();

    }

    /**
     * Converts a dot-decimal IPv4 string (e.g. "192.168.1.1") to a 32-bit unsigned long.
     */
    public static long ipToLong(String ip) {

        if (!isValidIpv4(ip)) {

            throw new IllegalArgumentException("Invalid IPv4 address format: " + ip);

        }

        String[] octets = ip.trim().split("\\.");

        long result = 0;

        for (int i = 0; i < 4; i++) {

            long octetVal = Long.parseLong(octets[i]);

            result |= (octetVal << (24 - (8 * i)));

        }

        return result & 0xFFFFFFFFL;

    }

    /**
     * Converts a 32-bit unsigned long back to a dot-decimal IPv4 string.
     */
    public static String longToIp(long ip) {

        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);

    }

    /**
     * Converts a CIDR prefix (0 to 32) to its 32-bit subnet mask representation.
     */
    public static long prefixToMaskLong(int prefix) {

        if (prefix < 0 || prefix > 32) {

            throw new IllegalArgumentException("CIDR prefix must be between 0 and 32, got: " + prefix);

        }

        if (prefix == 0) {

            return 0L;

        }

        return (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;

    }

    /**
     * Converts a CIDR prefix to its dot-decimal mask string (e.g. 24 -> "255.255.255.0").
     */
    public static String prefixToMaskString(int prefix) {

        return longToIp(prefixToMaskLong(prefix));

    }

    /**
     * Calculates the network address (start of subnet) for a given IP and prefix.
     */
    public static long getNetworkAddress(long ipLong, int prefix) {

        long mask = prefixToMaskLong(prefix);

        return ipLong & mask;

    }

    /**
     * Calculates the broadcast address (end of subnet) for a given network IP and prefix.
     */
    public static long getBroadcastAddress(long networkIpLong, int prefix) {

        long mask = prefixToMaskLong(prefix);

        return networkIpLong | (~mask & 0xFFFFFFFFL);

    }

    /**
     * Calculates total usable host IPs in a CIDR block.
     * /31 and /32 have 0 usable general hosts (RFC 3021 point-to-point / loopback).
     */
    public static long getUsableHostCount(int prefix) {

        if (prefix >= 31) {

            return 0L;

        }

        return (1L << (32 - prefix)) - 2L;

    }

    /**
     * Detects if two IP ranges [startA, endA] and [startB, endB] overlap in O(1) time.
     */
    public static boolean isOverlapping(long startA, long endA, long startB, long endB) {

        return Math.max(startA, startB) <= Math.min(endA, endB);

    }

    /**
     * Generates usable IP address strings broken down into bounded chunks of chunkSize.
     * Guarantees flat memory consumption even for large /16 or /20 subnets.
     */
    public static List<List<String>> generateIpChunks(long networkIp, long broadcastIp, int chunkSize) {

        if (chunkSize <= 0) {

            chunkSize = 512;

        }

        List<List<String>> chunks = new ArrayList<>();

        long firstUsable = networkIp + 1;

        long lastUsable = broadcastIp - 1;

        if (firstUsable > lastUsable) {

            return chunks;

        }

        List<String> currentChunk = new ArrayList<>(chunkSize);

        for (long ip = firstUsable; ip <= lastUsable; ip++) {

            currentChunk.add(longToIp(ip));

            if (currentChunk.size() >= chunkSize) {

                chunks.add(currentChunk);

                currentChunk = new ArrayList<>(chunkSize);

            }

        }

        if (!currentChunk.isEmpty()) {

            chunks.add(currentChunk);

        }

        return chunks;

    }

}
