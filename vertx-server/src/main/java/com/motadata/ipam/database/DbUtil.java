package com.motadata.ipam.database;

import io.vertx.sqlclient.Row;

/**
 * Enterprise Null-Safe Database Value Extractor.
 * Guarantees zero ClassCastException and zero NullPointerException across all MySQL/MariaDB queries.
 */
public final class DbUtil {

    private DbUtil() {

        // Prevent instantiation

    }

    /**
     * Safely extracts a Long from any numeric column (INT, BIGINT, TINYINT, COUNT(*)).
     */
    public static Long getLong(Row row, String col) {

        if (row == null || col == null) {

            return null;

        }

        try {

            Object val = row.getValue(col);

            return (val instanceof Number) ? ((Number) val).longValue() : null;

        } catch (Exception e) {

            return null;

        }

    }

    /**
     * Safely extracts a Long with a fallback default value if null or missing.
     */
    public static Long getLongOrDefault(Row row, String col, long defaultValue) {

        Long val = getLong(row, col);

        return val != null ? val : defaultValue;

    }

    /**
     * Safely extracts an Integer from any numeric column.
     */
    public static Integer getInt(Row row, String col) {

        if (row == null || col == null) {

            return null;

        }

        try {

            Object val = row.getValue(col);

            return (val instanceof Number) ? ((Number) val).intValue() : null;

        } catch (Exception e) {

            return null;

        }

    }

    /**
     * Safely extracts an Integer with a fallback default value if null or missing.
     */
    public static Integer getIntOrDefault(Row row, String col, int defaultValue) {

        Integer val = getInt(row, col);

        return val != null ? val : defaultValue;

    }

    /**
     * Safely extracts a String, converting Dates/LocalDateTime/Objects to clean strings.
     * Returns empty string "" if null to prevent downstream NullPointerExceptions.
     */
    public static String getString(Row row, String col) {

        if (row == null || col == null) {

            return "";

        }

        try {

            Object val = row.getValue(col);

            return (val != null) ? val.toString().trim() : "";

        } catch (Exception e) {

            return "";

        }

    }

    /**
     * Safely extracts a Boolean handling both BIT(1), TINYINT(1), and true/false values.
     */
    public static Boolean getBoolean(Row row, String col) {

        if (row == null || col == null) {

            return false;

        }

        try {

            Object val = row.getValue(col);

            if (val == null) {

                return false;

            }

            if (val instanceof Boolean) {

                return (Boolean) val;

            }

            if (val instanceof Number) {

                return ((Number) val).intValue() == 1;

            }

            if (val instanceof byte[]) {

                byte[] b = (byte[]) val;

                return b.length > 0 && b[0] == 1;

            }

            String s = val.toString().trim();

            return "1".equals(s) || "true".equalsIgnoreCase(s);

        } catch (Exception e) {

            return false;

        }

    }

}
