package com.motadata.ipam.core.database;

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
    // ===============
    // ==========================================================

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

    public static final String SCANNER_UPDATE_IP_STATUS_AVAILABLE =
            "UPDATE subnet_ip_details SET status = 'AVAILABLE' WHERE subnet_id_id = ? AND ip_address = ? AND status = 'USED'";

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

    // =========================================================================
    // Gateway & SNMP Discovery Queries
    // =========================================================================

    public static final String LIST_GATEWAYS =
            "SELECT id, name, gateway, version, community, security_level as securityLevel, " +
            "authentication_protocol as authenticationProtocol, privacy_protocol as privacyProtocol, " +
            "security_user_name as securityUserName, status, previous_scan as previousScan " +
            "FROM gateway ORDER BY id DESC";

    public static final String FIND_GATEWAY_BY_ID =
            "SELECT id, name, gateway, version, community, security_level as securityLevel, " +
            "authentication_protocol as authenticationProtocol, authentication_password as authenticationPassword, " +
            "privacy_protocol as privacyProtocol, private_password as privatePassword, " +
            "security_user_name as securityUserName, status, previous_scan as previousScan " +
            "FROM gateway WHERE id = ?";

    public static final String INSERT_GATEWAY =
            "INSERT INTO gateway (name, gateway, version, community, security_level, " +
            "authentication_protocol, authentication_password, privacy_protocol, private_password, security_user_name) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String DELETE_GATEWAY =
            "DELETE FROM gateway WHERE id = ?";

    public static final String UPDATE_GATEWAY_SCAN_TIME =
            "UPDATE gateway SET previous_scan = NOW(), status = ? WHERE id = ?";

    public static final String LIST_DISCOVERED_SUBNETS =
            "SELECT id, gateway, gateway_id as gatewayId, subnet, subnet_mask as subnetMask " +
            "FROM discovered_subnet ORDER BY id DESC";

    public static final String FIND_DISCOVERED_SUBNET_BY_ID =
            "SELECT id, gateway, gateway_id as gatewayId, subnet, subnet_mask as subnetMask " +
            "FROM discovered_subnet WHERE id = ?";

    public static final String INSERT_DISCOVERED_SUBNET =
            "INSERT INTO discovered_subnet (gateway, gateway_id, subnet, subnet_mask) " +
            "VALUES (?, ?, ?, ?)";

    public static final String DELETE_DISCOVERED_SUBNET =
            "DELETE FROM discovered_subnet WHERE id = ?";

    // =========================================================================
    // Event & Audit Notification Queries
    // =========================================================================

    public static final String COUNT_EVENTS =
            "SELECT COUNT(*) as total FROM event";

    public static final String LIST_EVENTS =
            "SELECT e.id, e.timestamp, e.event_type as eventType, e.event_context as eventContext, e.severity, u.user_name as userName " +
            "FROM event e LEFT JOIN user u ON e.done_by_id = u.id ORDER BY e.id DESC LIMIT ? OFFSET ?";

    public static final String LIST_TOP_EVENTS =
            "SELECT e.id, e.timestamp, e.event_type as eventType, e.event_context as eventContext, e.severity, u.user_name as userName " +
            "FROM event e LEFT JOIN user u ON e.done_by_id = u.id ORDER BY e.id DESC LIMIT 25";

    public static final String INSERT_EVENT =
            "INSERT INTO event (timestamp, event_type, event_context, severity, done_by_id) " +
            "VALUES (NOW(), ?, ?, ?, ?)";

    // =========================================================================
    // Alert & Alert Stream Queries
    // =========================================================================

    public static final String COUNT_ALERTS_ALL =
            "SELECT COUNT(*) as total FROM alert_stream";

    public static final String COUNT_ALERTS_ACTIVE =
            "SELECT COUNT(*) as total FROM alert_stream WHERE status = 1";

    public static final String LIST_ALERTS_ALL =
            "SELECT id, subnet_id as subnetId, alert_type as alertType, message, subnet, " +
            "timestamp, status FROM alert_stream ORDER BY id DESC LIMIT ? OFFSET ?";

    public static final String LIST_ALERTS_ACTIVE =
            "SELECT id, subnet_id as subnetId, alert_type as alertType, message, subnet, " +
            "timestamp, status FROM alert_stream WHERE status = 1 ORDER BY id DESC LIMIT ? OFFSET ?";

    public static final String INSERT_ALERT =
            "INSERT INTO alert_stream (subnet_id, alert_type, message, subnet, timestamp, status) " +
            "VALUES (?, ?, ?, ?, NOW(), 1)";

    public static final String CLEAR_ALERT_BY_ID =
            "UPDATE alert_stream SET status = 0 WHERE id = ?";

    public static final String CLEAR_ALERTS_BY_SUBNET =
            "UPDATE alert_stream SET status = 0 WHERE subnet_id = ?";

    public static final String DELETE_ALERT_BY_ID =
            "DELETE FROM alert_stream WHERE id = ?";

    // =========================================================================
    // Rogue Detection Queries
    // =========================================================================

    public static final String COUNT_ROGUE_DEVICES =
            "SELECT COUNT(*) as total FROM rogue_detection_details";

    public static final String LIST_ROGUE_DEVICES =
            "SELECT id, mac_address as macAddress, ip_address as ipAddress, " +
            "discovered_at as discoveredAt, nic_type as nicType, authenticity " +
            "FROM rogue_detection_details ORDER BY id DESC LIMIT ? OFFSET ?";

    public static final String INSERT_ROGUE_DEVICE =
            "INSERT INTO rogue_detection_details (mac_address, ip_address, discovered_at, nic_type, authenticity) " +
            "VALUES (?, ?, NOW(), ?, ?)";

    public static final String UPDATE_ROGUE_AUTHENTICITY =
            "UPDATE rogue_detection_details SET authenticity = ? WHERE id = ?";

    public static final String DELETE_ROGUE_DEVICE =
            "DELETE FROM rogue_detection_details WHERE id = ?";

    // =========================================================================
    // User & Role Management Queries
    // =========================================================================

    public static final String COUNT_USERS =
            "SELECT COUNT(*) as total FROM user";

    public static final String LIST_ALL_USERS =
            "SELECT u.id, u.user_name as userName, u.email, u.description, u.status, u.user_role_id_id as userRoleId, " +
            "r.role as roleName, u.current_login_status as currentLoginStatus, u.previous_login_status as previousLoginStatus " +
            "FROM user u LEFT JOIN user_role r ON u.user_role_id_id = r.id ORDER BY u.id ASC";

    public static final String GET_USER_BY_ID =
            "SELECT u.id, u.user_name as userName, u.email, u.description, u.status, u.user_role_id_id as userRoleId, " +
            "r.role as roleName, u.current_login_status as currentLoginStatus, u.previous_login_status as previousLoginStatus " +
            "FROM user u LEFT JOIN user_role r ON u.user_role_id_id = r.id WHERE u.id = ?";

    public static final String FIND_USER_BY_NAME_OR_EMAIL =
            "SELECT id FROM user WHERE user_name = ? OR email = ?";

    public static final String INSERT_USER =
            "INSERT INTO user (user_name, email, password, description, status, user_role_id_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    public static final String UPDATE_USER =
            "UPDATE user SET email = ?, description = ?, status = ?, user_role_id_id = ? WHERE id = ?";

    public static final String UPDATE_USER_PASSWORD =
            "UPDATE user SET password = ? WHERE id = ?";

    public static final String DELETE_USER_BY_ID =
            "DELETE FROM user WHERE id = ?";

    public static final String LIST_USER_ROLES =
            "SELECT id, role as roleName, description FROM user_role ORDER BY id ASC";

    public static final String FIND_USER_ROLE_BY_ID =
            "SELECT id, role as roleName, description FROM user_role WHERE id = ?";

    public static final String INSERT_USER_ROLE =
            "INSERT INTO user_role (role, description) VALUES (?, ?)";

    public static final String UPDATE_USER_ROLE =
            "UPDATE user_role SET role = ?, description = ? WHERE id = ?";

    public static final String DELETE_USER_ROLE =
            "DELETE FROM user_role WHERE id = ?";

    public static final String LIST_FEATURES =
            "SELECT id, name as featureName FROM feature ORDER BY id ASC";

    public static final String LIST_ROLE_FEATURE_PERMISSIONS =
            "SELECT rfp.id, rfp.feature_id as featureId, f.name as featureName, " +
            "rfp.read_permission as readPermission, rfp.write_permission as writePermission " +
            "FROM feature f LEFT JOIN role_feature_permission rfp ON f.id = rfp.feature_id AND rfp.role_id = ? " +
            "ORDER BY f.id ASC";

    public static final String DELETE_ROLE_PERMISSIONS =
            "DELETE FROM role_feature_permission WHERE role_id = ?";

    public static final String INSERT_ROLE_PERMISSION =
            "INSERT INTO role_feature_permission (role_id, feature_id, read_permission, write_permission) VALUES (?, ?, ?, ?)";

    // =========================================================================
    // Database Maintenance & Data Retention Queries
    // =========================================================================

    public static final String GET_DATABASE_MAINTENANCE =
            "SELECT id, backup_path as backupPath, duration, maintained_days as maintainedDays, " +
            "schedule_hour as scheduleHour, schedule_status as scheduleStatus, status " +
            "FROM database_maintainence WHERE id = 1";

    public static final String UPDATE_DATABASE_MAINTENANCE =
            "UPDATE database_maintainence SET maintained_days = ?, status = ?, schedule_status = ? WHERE id = 1";

    public static final String PURGE_OLD_EVENTS =
            "DELETE FROM event WHERE timestamp < DATE_SUB(NOW(), INTERVAL ? DAY)";

    public static final String PURGE_OLD_IP_CHANGE_LOGS =
            "DELETE FROM ip_change_log WHERE timestamp < DATE_SUB(NOW(), INTERVAL ? DAY)";

    public static final String PURGE_OLD_ALERTS =
            "DELETE FROM alert_stream WHERE timestamp < DATE_SUB(NOW(), INTERVAL ? DAY)";

}

