package com.motadata.ipam.user;


import com.motadata.ipam.core.database.DbQueries;
import com.motadata.ipam.core.database.DbUtil;
import com.motadata.ipam.event.EventService;
import com.motadata.ipam.security.PasswordUtil;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonArray;

import io.vertx.core.json.JsonObject;

import io.vertx.sqlclient.Pool;

import io.vertx.sqlclient.Row;

import io.vertx.sqlclient.Tuple;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

/**
 * Enterprise User & Role Management Service.
 * Implements user CRUD, secure PBKDF2 password hashing, role mapping, and audit logging.
 */
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final Pool mysqlPool;

    private final Vertx vertx;

    public UserService(Pool mysqlPool, Vertx vertx) {

        this.mysqlPool = mysqlPool;

        this.vertx = vertx;

    }

    public UserService(Pool mysqlPool) {

        this(mysqlPool, null);

    }

    /**
     * Lists all users in the system.
     */
    public Future<JsonArray> listUsers() {

        return mysqlPool.preparedQuery(DbQueries.LIST_ALL_USERS).execute()
                .map(rows -> {

                    JsonArray users = new JsonArray();

                    for (Row row : rows) {

                        users.add(mapUserRow(row));

                    }

                    return users;

                });

    }

    /**
     * Retrieves a user by their unique primary key ID.
     */
    public Future<JsonObject> getUserById(Long userId) {

        if (userId == null || userId <= 0) {

            return Future.failedFuture("Invalid user ID");

        }

        return mysqlPool.preparedQuery(DbQueries.GET_USER_BY_ID)
                .execute(Tuple.of(userId))
                .compose(rows -> {

                    if (!rows.iterator().hasNext()) {

                        return Future.failedFuture("User not found");

                    }

                    return Future.succeededFuture(mapUserRow(rows.iterator().next()));

                });

    }

    /**
     * Creates a new user account with hashed password and role assignment.
     */
    public Future<JsonObject> createUser(JsonObject payload) {

        if (payload == null) {

            return Future.failedFuture("User payload is required");

        }

        String username = payload.getString("userName", payload.getString("username"));

        String email = payload.getString("email");

        String rawPassword = payload.getString("password");

        String description = payload.getString("description", "");

        boolean status = extractStatus(payload);

        Long roleId = extractRoleId(payload, 2L);

        if (username == null || username.isBlank()) {

            return Future.failedFuture("Username is required");

        }

        if (email == null || email.isBlank()) {

            return Future.failedFuture("Email is required");

        }

        if (rawPassword == null || rawPassword.isBlank()) {

            return Future.failedFuture("Password is required");

        }

        final String finalUsername = username.trim();

        final String finalEmail = email.trim();

        return mysqlPool.preparedQuery(DbQueries.FIND_USER_BY_NAME_OR_EMAIL)
                .execute(Tuple.of(finalUsername, finalEmail))
                .compose(existing -> {

                    if (existing.iterator().hasNext()) {

                        return Future.failedFuture("User with username '" + finalUsername + "' or email '" + finalEmail + "' already exists");

                    }

                    String hashedPassword = PasswordUtil.hash(rawPassword);

                    int statusBit = status ? 1 : 0;

                    Tuple params = Tuple.of(finalUsername, finalEmail, hashedPassword, description, statusBit, roleId);

                    return mysqlPool.preparedQuery(DbQueries.INSERT_USER).execute(params);

                })
                .compose(result -> {

                    Long newUserId = result.property(io.vertx.mysqlclient.MySQLClient.LAST_INSERTED_ID);

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "USER_CREATED")
                                .put("eventContext", "Created user account '" + finalUsername + "' with role ID " + roleId)
                                .put("severity", 1));

                    }

                    return getUserById(newUserId != null ? newUserId : 1L);

                });

    }

    /**
     * Updates an existing user's email, description, active status, and role.
     */
    public Future<JsonObject> updateUser(Long userId, JsonObject payload) {

        if (userId == null || userId <= 0) {

            return Future.failedFuture("Invalid user ID");

        }

        if (payload == null) {

            return Future.failedFuture("User payload is required");

        }

        String email = payload.getString("email", "");

        String description = payload.getString("description", "");

        boolean status = extractStatus(payload);

        Long roleId = extractRoleId(payload, 2L);

        int statusBit = status ? 1 : 0;

        Tuple params = Tuple.of(email, description, statusBit, roleId, userId);

        return mysqlPool.preparedQuery(DbQueries.UPDATE_USER)
                .execute(params)
                .compose(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "USER_UPDATED")
                                .put("eventContext", "Updated details for User ID " + userId)
                                .put("severity", 1));

                    }

                    return getUserById(userId);

                });

    }

    /**
     * Updates a user's password securely with PBKDF2 hashing.
     */
    public Future<Void> changePassword(Long userId, String newPassword) {

        if (userId == null || userId <= 0) {

            return Future.failedFuture("Invalid user ID");

        }

        if (newPassword == null || newPassword.isBlank()) {

            return Future.failedFuture("New password cannot be empty");

        }

        String hashedPassword = PasswordUtil.hash(newPassword);

        return mysqlPool.preparedQuery(DbQueries.UPDATE_USER_PASSWORD)
                .execute(Tuple.of(hashedPassword, userId))
                .compose(res -> {

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "PASSWORD_CHANGED")
                                .put("eventContext", "Changed password for User ID " + userId)
                                .put("severity", 1));

                    }

                    return Future.succeededFuture();

                });

    }

    /**
     * Deletes a user account with safety checks (cannot delete user 1 / admin).
     */
    public Future<JsonObject> deleteUser(Long userId, Long currentUserId) {

        if (userId == null || userId <= 0) {

            return Future.failedFuture("Invalid user ID");

        }

        if (userId == 1L) {

            return Future.failedFuture("Primary administrator account cannot be deleted");

        }

        if (currentUserId != null && userId.equals(currentUserId)) {

            return Future.failedFuture("You cannot delete your own logged-in account");

        }

        // Unlink foreign key references before deleting user to avoid FK error 1451
        return mysqlPool.preparedQuery("UPDATE event SET done_by_id = NULL WHERE done_by_id = ?")
                .execute(Tuple.of(userId))
                .compose(v -> mysqlPool.preparedQuery("DELETE FROM forgot_password WHERE user_id = ?").execute(Tuple.of(userId)))
                .compose(v -> mysqlPool.preparedQuery(DbQueries.DELETE_USER_BY_ID).execute(Tuple.of(userId)))
                .map(res -> {

                    JsonObject result = new JsonObject();

                    result.put("deleted", true);

                    result.put("userId", userId);

                    if (vertx != null && vertx.eventBus() != null) {

                        vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                .put("eventType", "USER_DELETED")
                                .put("eventContext", "Deleted User ID " + userId + " from system")
                                .put("severity", 2));

                    }

                    return result;

                });

    }

    /**
     * Lists all user roles in the system.
     */
    public Future<JsonArray> listRoles() {

        return mysqlPool.preparedQuery(DbQueries.LIST_USER_ROLES).execute()
                .map(rows -> {

                    JsonArray roles = new JsonArray();

                    for (Row row : rows) {

                        JsonObject r = new JsonObject();

                        r.put("id", DbUtil.getLong(row, "id"));

                        r.put("role", DbUtil.getString(row, "roleName"));

                        r.put("roleName", DbUtil.getString(row, "roleName"));

                        r.put("description", DbUtil.getString(row, "description"));

                        roles.add(r);

                    }

                    return roles;

                });

    }

    /**
     * Retrieves a specific user role with its feature permissions.
     */
    public Future<JsonObject> getRoleById(Long roleId) {

        if (roleId == null || roleId <= 0) {

            return Future.failedFuture("Invalid role ID");

        }

        return mysqlPool.preparedQuery(DbQueries.FIND_USER_ROLE_BY_ID)
                .execute(Tuple.of(roleId))
                .compose(rows -> {

                    if (!rows.iterator().hasNext()) {

                        return Future.failedFuture("Role not found with ID: " + roleId);

                    }

                    Row row = rows.iterator().next();

                    JsonObject roleObj = new JsonObject();

                    roleObj.put("id", DbUtil.getLong(row, "id"));

                    roleObj.put("role", DbUtil.getString(row, "roleName"));

                    roleObj.put("roleName", DbUtil.getString(row, "roleName"));

                    roleObj.put("description", DbUtil.getString(row, "description"));

                    return getRolePermissions(roleId).map(permissions -> {

                        roleObj.put("roleFeaturePermissions", permissions);

                        roleObj.put("permissions", permissions);

                        return roleObj;

                    });

                });

    }

    /**
     * Lists all system features for role permission assignment.
     */
    public Future<JsonArray> listFeatures() {

        return mysqlPool.preparedQuery(DbQueries.LIST_FEATURES).execute()
                .map(rows -> {

                    JsonArray features = new JsonArray();

                    for (Row row : rows) {

                        JsonObject f = new JsonObject();

                        f.put("id", DbUtil.getLong(row, "id"));

                        f.put("featureName", DbUtil.getString(row, "featureName"));

                        f.put("read", false);

                        f.put("write", false);

                        features.add(f);

                    }

                    return features;

                });

    }

    /**
     * Retrieves features with read/write permissions for a specific role.
     */
    public Future<JsonArray> getRolePermissions(Long roleId) {

        if (roleId == null || roleId <= 0) {

            return Future.failedFuture("Invalid role ID");

        }

        return mysqlPool.preparedQuery(DbQueries.LIST_ROLE_FEATURE_PERMISSIONS)
                .execute(Tuple.of(roleId))
                .map(rows -> {

                    JsonArray permissions = new JsonArray();

                    for (Row row : rows) {

                        JsonObject p = new JsonObject();

                        p.put("id", DbUtil.getLong(row, "id"));

                        p.put("featureId", DbUtil.getLong(row, "featureId"));

                        p.put("featureName", DbUtil.getString(row, "featureName"));

                        p.put("read", DbUtil.getBoolean(row, "readPermission"));

                        p.put("write", DbUtil.getBoolean(row, "writePermission"));

                        permissions.add(p);

                    }

                    return permissions;

                });

    }

    /**
     * Creates a new user role with feature permissions.
     */
    public Future<JsonObject> createRole(JsonObject payload) {

        if (payload == null) {

            return Future.failedFuture("Role payload is required");

        }

        String roleName = payload.getString("roleName", payload.getString("role"));

        String description = payload.getString("description", "");

        if (roleName == null || roleName.isBlank()) {

            return Future.failedFuture("Role name is required");

        }

        final String finalRoleName = roleName.trim();

        Tuple params = Tuple.of(finalRoleName, description);

        return mysqlPool.preparedQuery(DbQueries.INSERT_USER_ROLE)
                .execute(params)
                .compose(res -> {

                    Long newRoleId = res.property(io.vertx.mysqlclient.MySQLClient.LAST_INSERTED_ID);

                    JsonArray permissions = payload.getJsonArray("permissions");

                    Future<Void> permFuture = Future.succeededFuture();

                    if (permissions != null && !permissions.isEmpty()) {

                        java.util.List<Tuple> batch = new java.util.ArrayList<>();

                        for (int i = 0; i < permissions.size(); i++) {

                            JsonObject p = permissions.getJsonObject(i);

                            if (p != null) {

                                Long featureId = p.getLong("id", (long) (i + 1));

                                boolean read = p.getBoolean("read", false);

                                boolean write = p.getBoolean("write", false);

                                batch.add(Tuple.of(newRoleId, featureId, read ? 1 : 0, write ? 1 : 0));

                            }

                        }

                        if (!batch.isEmpty()) {

                            permFuture = mysqlPool.preparedQuery(DbQueries.INSERT_ROLE_PERMISSION)
                                    .executeBatch(batch)
                                    .mapEmpty();

                        }

                    }

                    return permFuture.map(v -> {

                        if (vertx != null && vertx.eventBus() != null) {

                            vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                    .put("eventType", "ROLE_CREATED")
                                    .put("eventContext", "Created new user role '" + finalRoleName + "'")
                                    .put("severity", 1));

                        }

                        JsonObject created = new JsonObject();

                        created.put("id", newRoleId);

                        created.put("role", finalRoleName);

                        created.put("roleName", finalRoleName);

                        created.put("description", description);

                        return created;

                    });

                });

    }

    /**
     * Updates an existing user role and its feature permissions.
     */
    public Future<JsonObject> updateRole(Long roleId, JsonObject payload) {

        if (roleId == null || roleId <= 0) {

            return Future.failedFuture("Invalid role ID");

        }

        if (payload == null) {

            return Future.failedFuture("Role payload is required");

        }

        String roleName = payload.getString("roleName", payload.getString("role", "ROLE_USER"));

        String description = payload.getString("description", "");

        Tuple params = Tuple.of(roleName, description, roleId);

        return mysqlPool.preparedQuery(DbQueries.UPDATE_USER_ROLE)
                .execute(params)
                .compose(res -> {

                    JsonArray permissions = payload.getJsonArray("permissions");

                    Future<Void> permFuture = Future.succeededFuture();

                    if (permissions != null) {

                        permFuture = mysqlPool.preparedQuery(DbQueries.DELETE_ROLE_PERMISSIONS)
                                .execute(Tuple.of(roleId))
                                .compose(delRes -> {

                                    java.util.List<Tuple> batch = new java.util.ArrayList<>();

                                    for (int i = 0; i < permissions.size(); i++) {

                                        JsonObject p = permissions.getJsonObject(i);

                                        if (p != null) {

                                            Long featureId = p.getLong("id", (long) (i + 1));

                                            boolean read = p.getBoolean("read", false);

                                            boolean write = p.getBoolean("write", false);

                                            batch.add(Tuple.of(roleId, featureId, read ? 1 : 0, write ? 1 : 0));

                                        }

                                    }

                                    if (!batch.isEmpty()) {

                                        return mysqlPool.preparedQuery(DbQueries.INSERT_ROLE_PERMISSION)
                                                .executeBatch(batch)
                                                .mapEmpty();

                                    }

                                    return Future.succeededFuture();

                                });

                    }

                    return permFuture.map(v -> {

                        if (vertx != null && vertx.eventBus() != null) {

                            vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                    .put("eventType", "ROLE_UPDATED")
                                    .put("eventContext", "Updated role '" + roleName + "' (ID: " + roleId + ")")
                                    .put("severity", 1));

                        }

                        JsonObject updated = new JsonObject();

                        updated.put("id", roleId);

                        updated.put("role", roleName);

                        updated.put("roleName", roleName);

                        updated.put("description", description);

                        return updated;

                    });

                });

    }

    /**
     * Deletes a user role safely (guards against deleting role 1 / ROLE_ADMIN, unlinks permissions).
     */
    public Future<JsonObject> deleteRole(Long roleId) {

        if (roleId == null || roleId <= 0) {

            return Future.failedFuture("Invalid role ID");

        }

        if (roleId == 1L) {

            return Future.failedFuture("Primary administrator role cannot be deleted");

        }

        return mysqlPool.preparedQuery("SELECT COUNT(*) as userCount FROM user WHERE user_role_id_id = ?")
                .execute(Tuple.of(roleId))
                .compose(rows -> {

                    long count = 0;

                    if (rows.iterator().hasNext()) {

                        count = DbUtil.getLongOrDefault(rows.iterator().next(), "userCount", 0L);

                    }

                    if (count > 0) {

                        return Future.failedFuture("Cannot delete role because " + count + " active user(s) are assigned to it");

                    }

                    return mysqlPool.preparedQuery(DbQueries.DELETE_ROLE_PERMISSIONS)
                            .execute(Tuple.of(roleId))
                            .compose(delPerms -> mysqlPool.preparedQuery(DbQueries.DELETE_USER_ROLE).execute(Tuple.of(roleId)))
                            .map(res -> {

                                if (vertx != null && vertx.eventBus() != null) {

                                    vertx.eventBus().send(EventService.ADDRESS_EVENT_LOG, new JsonObject()
                                            .put("eventType", "ROLE_DELETED")
                                            .put("eventContext", "Deleted user role ID " + roleId)
                                            .put("severity", 2));

                                }

                                JsonObject result = new JsonObject();

                                result.put("deleted", true);

                                result.put("roleId", roleId);

                                return result;

                            });

                });

    }

    private JsonObject mapUserRow(Row row) {

        JsonObject user = new JsonObject();

        user.put("id", DbUtil.getLong(row, "id"));

        user.put("userName", DbUtil.getString(row, "userName"));

        user.put("username", DbUtil.getString(row, "userName"));

        user.put("email", DbUtil.getString(row, "email"));

        user.put("description", DbUtil.getString(row, "description"));

        user.put("status", DbUtil.getBoolean(row, "status"));

        Long roleId = DbUtil.getLong(row, "userRoleId");

        String roleName = DbUtil.getString(row, "roleName");

        user.put("userRoleId", roleId);

        user.put("roleId", roleId);

        user.put("role", roleName);

        user.put("roleName", roleName);

        JsonObject roleObj = new JsonObject();

        roleObj.put("id", roleId);

        roleObj.put("role", roleName);

        roleObj.put("roleName", roleName);

        user.put("userRoleIdObj", roleObj);

        user.put("currentLoginStatus", DbUtil.getString(row, "currentLoginStatus"));

        user.put("previousLoginStatus", DbUtil.getString(row, "previousLoginStatus"));

        return user;

    }

    private boolean extractStatus(JsonObject payload) {

        if (payload == null) {

            return true;

        }

        Object rawStatus = payload.getValue("activeStatus", payload.getValue("status"));

        if (rawStatus instanceof Boolean) {

            return (Boolean) rawStatus;

        }

        if (rawStatus instanceof String) {

            String s = ((String) rawStatus).trim().toLowerCase();

            return "enable".equals(s) || "enabled".equals(s) || "true".equals(s) || "1".equals(s);

        }

        if (rawStatus instanceof Number) {

            return ((Number) rawStatus).intValue() == 1;

        }

        return true;

    }

    private Long extractRoleId(JsonObject payload, Long defaultRoleId) {

        if (payload == null) {

            return defaultRoleId;

        }

        Object rawRole = payload.getValue("roleId", payload.getValue("userRoleId"));

        if (rawRole instanceof Number) {

            return ((Number) rawRole).longValue();

        }

        if (rawRole instanceof String && !((String) rawRole).isBlank()) {

            try {

                return Long.parseLong(((String) rawRole).trim());

            } catch (Exception ignored) {

                return defaultRoleId;

            }

        }

        return defaultRoleId;

    }

}
