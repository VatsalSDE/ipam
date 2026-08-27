package com.motadata.ipam.database;

/**
 * Centralized Database Query Constants.
 * Consolidates all raw SQL statements in one place with clean variable names.
 * Uses exact MySQL schema column names with camelCase aliases for zero-overhead JSON mapping.
 */
public final class DbQueries {

    private DbQueries() {

        // Prevent instantiation

    }

    // =========================================================================
    // Authentication & User Queries
    // =========================================================================

    public static final String FIND_USER_BY_USERNAME =
            "SELECT u.id, u.user_name as userName, u.password, u.email, u.status, u.user_role_id_id as userRoleId_id, r.role as role_name " +
            "FROM user u LEFT JOIN user_role r ON u.user_role_id_id = r.id WHERE u.user_name = ?";

    public static final String FIND_USER_BY_ID =
            "SELECT u.id, u.user_name as userName, u.email, u.status, u.user_role_id_id as userRoleId_id, r.role as role_name " +
            "FROM user u LEFT JOIN user_role r ON u.user_role_id_id = r.id WHERE u.id = ?";

    public static final String CHECK_USER_ACTIVE =
            "SELECT u.id, u.user_name as userName, u.email, u.status, u.user_role_id_id as userRoleId_id, r.role as role_name " +
            "FROM user u LEFT JOIN user_role r ON u.user_role_id_id = r.id WHERE u.id = ? AND u.status = 1";


    public static final String FETCH_ROLE_PERMISSIONS =
            "SELECT f.name as feature_name, rfp.read_permission, rfp.write_permission " +
            "FROM role_feature_permission rfp JOIN feature f ON rfp.feature_id = f.id WHERE rfp.role_id = ?";

    public static final String UPDATE_USER_LOGIN_STATUS =
            "UPDATE user SET previous_login_status = current_login_status, current_login_status = NOW() WHERE id = ?";

    // =========================================================================
    // Subnet Management Queries
    // =========================================================================

    public static final String COUNT_SUBNETS_ALL =
            "SELECT COUNT(*) as total FROM subnet_details";

    public static final String COUNT_SUBNETS_SEARCH =
            "SELECT COUNT(*) as total FROM subnet_details WHERE subnet_name LIKE ? OR subnet_address LIKE ?";

    public static final String LIST_SUBNETS_ALL =
            "SELECT id, subnet_name as subnetName, subnet_address as subnetAddress, subnet_cidr as subnetCidr, " +
            "subnet_mask as subnetMask, description, location, total_ip as totalIp, available_ip as availableIp, " +
            "used_ip as usedIp, schedule_status as scheduleStatus, schedule_hour as scheduleHour, " +
            "last_scan_time as lastScanTime, created_date as createdDate, modified_date as modifiedDate " +
            "FROM subnet_details ORDER BY id DESC LIMIT ? OFFSET ?";

    public static final String LIST_SUBNETS_SEARCH =
            "SELECT id, subnet_name as subnetName, subnet_address as subnetAddress, subnet_cidr as subnetCidr, " +
            "subnet_mask as subnetMask, description, location, total_ip as totalIp, available_ip as availableIp, " +
            "used_ip as usedIp, schedule_status as scheduleStatus, schedule_hour as scheduleHour, " +
            "last_scan_time as lastScanTime, created_date as createdDate, modified_date as modifiedDate " +
            "FROM subnet_details WHERE subnet_name LIKE ? OR subnet_address LIKE ? " +
            "ORDER BY id DESC LIMIT ? OFFSET ?";

    public static final String GET_SUBNET_BY_ID =
            "SELECT id, subnet_name as subnetName, subnet_address as subnetAddress, subnet_cidr as subnetCidr, " +
            "subnet_mask as subnetMask, description, location, total_ip as totalIp, available_ip as availableIp, " +
            "used_ip as usedIp, schedule_status as scheduleStatus, schedule_hour as scheduleHour, " +
            "last_scan_time as lastScanTime, created_date as createdDate, modified_date as modifiedDate " +
            "FROM subnet_details WHERE id = ?";

    public static final String GET_SUBNET_STATUS_BREAKDOWN =
            "SELECT status, COUNT(*) as count FROM subnet_ip_details WHERE subnet_id_id = ? GROUP BY status";

    public static final String FIND_SUBNET_ID_BY_NAME =
            "SELECT id FROM subnet_details WHERE subnet_name = ?";

    public static final String GET_ALL_SUBNETS_FOR_OVERLAP =
            "SELECT id, subnet_name as subnetName, subnet_address as subnetAddress, subnet_cidr as subnetCidr FROM subnet_details";

    public static final String INSERT_SUBNET =
            "INSERT INTO subnet_details " +
            "(subnet_name, subnet_address, subnet_cidr, subnet_mask, description, location, " +
            "total_ip, used_ip, available_ip, schedule_status, schedule_hour, " +
            "is_local_subnet, allow_icmp, allow_dns, is_ipv6, created_date, modified_date) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 1, 1, 0, 0, NOW(), NOW())";

    public static final String INSERT_SUBNET_IPS_BATCH =
            "INSERT INTO subnet_ip_details (subnet_id_id, ip_address, status, authenticity, created_date, deactive_status) " +
            "VALUES (?, ?, 'AVAILABLE', 'TRUSTED', NOW(), 0)";

    public static final String DELETE_SUBNET_IPS =
            "DELETE FROM subnet_ip_details WHERE subnet_id_id = ?";

    public static final String DELETE_SUBNET_BY_ID =
            "DELETE FROM subnet_details WHERE id = ?";

    // =========================================================================
    // Subnet IP Address Queries
    // =========================================================================

    public static final String COUNT_SUBNET_IPS_BASE =
            "SELECT COUNT(*) as total FROM subnet_ip_details WHERE subnet_id_id = ?";

    public static final String SELECT_SUBNET_IPS_BASE =
            "SELECT id, ip_address, mac_address, status, device_type, host_name, authenticity " +
            "FROM subnet_ip_details WHERE subnet_id_id = ?";

    // =========================================================================
    // Scanner & Scheduler Queries
    // =========================================================================

    public static final String SCANNER_FETCH_SUBNET =
            "SELECT id, subnet_name as subnetName, subnet_address as subnetAddress, subnet_cidr as subnetCidr " +
            "FROM subnet_details WHERE id = ?";

    public static final String SCANNER_COUNT_IPS =
            "SELECT COUNT(*) as total FROM subnet_ip_details WHERE subnet_id_id = ?";

    public static final String SCANNER_FETCH_IP_CHUNK =
            "SELECT id, ip_address FROM subnet_ip_details WHERE subnet_id_id = ? AND id > ? ORDER BY id ASC LIMIT ?";

    public static final String SCANNER_UPDATE_IP_STATUS_USED =
            "UPDATE subnet_ip_details SET status = 'USED' WHERE subnet_id_id = ? AND ip_address = ?";

    public static final String SCANNER_RECALCULATE_COUNTERS =
            "UPDATE subnet_details sd " +
            "JOIN (" +
            "    SELECT " +
            "        subnet_id_id, " +
            "        SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END) as used_count, " +
            "        SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END) as avail_count " +
            "    FROM subnet_ip_details " +
            "    WHERE subnet_id_id = ? " +
            "    GROUP BY subnet_id_id" +
            ") counts ON sd.id = counts.subnet_id_id " +
            "SET sd.used_ip = counts.used_count, sd.available_ip = counts.avail_count " +
            "WHERE sd.id = ?";

    public static final String SCHEDULER_FIND_DUE_SUBNETS =
            "SELECT id, subnet_name as subnetName, schedule_hour as scheduleHour, last_scan_time as lastScanTime " +
            "FROM subnet_details WHERE schedule_status = 1";

}
