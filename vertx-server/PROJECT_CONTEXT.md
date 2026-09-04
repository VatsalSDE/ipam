# Motadata IPAM - Project Context & Code Review Reference

---

## 1. System Architecture & Tech Stack

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  MOTADATA IPAM VERT.X ENGINE ARCHITECTURE                              │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                        │
│  [ Web Browser / Kendo UI Frontend ]                                                                  │
│           │                                                                                            │
│           │ HTTP / JSON (JWT Bearer Token / Cookie Auth)                                               │
│           ▼                                                                                            │
│  [ Vert.x HTTP Server (Port 8888) - MainVerticle ]                                                     │
│           │                                                                                            │
│           ├── RbacAuthHandler ──► Token validation & Permission Checks (PERM_*, ROLE_*)               │
│           │                                                                                            │
│           ├── Core API Routers:                                                                        │
│           │   ├── /api/auth               ──► Login, Refresh, Me (AuthRouter)                          │
│           │   ├── /api/dashboard/summary  ──► Composite Non-Blocking Aggregation (DashboardRouter)   │
│           │   ├── /api/subnet             ──► Subnet CRUD, IP Explorer, Overlaps (SubnetRouter)        │
│           │   ├── /api/gateway            ──► SNMP ARP Discovery (GatewayRouter)                       │
│           │   ├── /api/event              ──► Audit Trail & 12-Month Sparkline (EventRouter)           │
│           │   ├── /api/alerts             ──► Threshold & Conflict Notifications (AlertRouter)         │
│           │   ├── /api/rogue-detection    ──► MAC Authenticity Tracking (RogueDetectionRouter)         │
│           │   ├── /api/user               ──► User CRUD & Roles (UserRouter)                           │
│           │   └── /api/database-maintenance ► Data Retention & Auto-Purge (DatabaseMaintenanceRouter) │
│           │                                                                                            │
│           ├── Vert.x EventBus Worker Pools:                                                            │
│           │   ├── SubnetWorkerVerticle   ──► Memory-safe chunked IP generation (Tuple batches)         │
│           │   └── ScanWorkerVerticle     ──► Go binary ICMP ping & SNMP scan execution                 │
│           │                                                                                            │
│           └── Reactive Database Layer:                                                                 │
│               └── DatabasePool (io.vertx.mysqlclient.MySQLPool) ──► MariaDB / MySQL (Port 3306)        │
│                                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Complete Summary of Code Changes & Features

### 🔹 **1. Unified Resilient Dashboard Service (`DashboardService.java` & `DashboardRouter.java`)**
* **Location**: [`DashboardService.java`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/java/com/motadata/ipam/dashboard/DashboardService.java)
* **What It Does**:
  * Implements non-blocking aggregation using `CompositeFuture.all` combining 7 parallel futures:
    1. `SubnetService.getSummary()` (Total subnets, used IPs, available IPs, capacity %).
    2. `GatewayService.getDiscoveredSubnets()` (Live gateway ARP discovery count).
    3. `EventService.get12MonthSummary()` (12-month rolling activity sparkline).
    4. `RogueDetectionService.getSummary()` (Discovered, Rogue, and Trusted devices).
    5. `AlertService.getActiveAlertsCount()` (Active threshold alerts).
    6. `SubnetService.getTopSubnets(10)` (Top 10 utilized subnets).
    7. `DashboardService.getVendorDistribution()` (IEEE OUI hardware vendor distribution).
  * **Resilience (`.recover()`)**: Each future is individually wrapped with fallback defaults, ensuring that even if one database query is empty or fails, the dashboard will **never crash**.

---

### 🔹 **2. 12-Month IP & Event Activity Sparkline (`EventService.java` & `widget.js`)**
* **Location**: [`EventService.java`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/java/com/motadata/ipam/event/EventService.java#L170-L225) & [`widget.js`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/resources/webroot/js/motadata/widget.js#L620-L648)
* **What It Does**:
  * Query: `GET_12_MONTH_EVENT_SUMMARY` in `DbQueries.java`.
  * Generates a rolling 12-month timeline from current month backwards (`Oct` to `Sep`).
  * Applies dynamic severity coloring to bars:
    * **Red (`#FF0000`)** - Severity 1 (Critical: Rogue detected, IP conflict).
    * **Orange (`#FFA31A`)** - Severity 2 (Warning: High utilization $\ge 80\%$).
    * **Blue (`#00b3ee`)** - Severity 3 (Normal: System operations, login events).
  * Sized sparkline chart to `height: 52px` with `8px` font and hover tooltips (`"Aug 2026: 5 Events"`), preventing card overflow.

---

### 🔹 **3. Ping Latency & High-Speed Subnet Scanner (`ScanWorkerVerticle.java` & Go Engine)**
* **Location**: [`ScanWorkerVerticle.java`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/java/com/motadata/ipam/scanner/ScanWorkerVerticle.java) & `go-plugins/cmd/ipam-engine/`
* **What It Does**:
  * Integrated native Go ICMP ping scanner with concurrent goroutines and optimized ping timeouts (**300–500ms**).
  * Drastically reduced scan sweep times for large `/20` (4,096 IPs) and `/22` (1,024 IPs) subnets from minutes to seconds.
  * Updates IP state transitions: `USED` (Active), `AVAILABLE` (Free), and `TRANSIENT` (Grace period).

---

### 🔹 **4. Rogue Device & Authenticity Classification (`RogueDetectionService.java`)**
* **Location**: [`RogueDetectionService.java`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/java/com/motadata/ipam/rogue/RogueDetectionService.java)
* **What It Does**:
  * Automatically evaluates all scanned MAC addresses.
  * Classifies endpoints into:
    * **Discovered (3)**: Newly seen MACs awaiting administrator review.
    * **Rogue (1)**: Unapproved devices detected on secure subnets.
    * **Trusted (1)**: Approved corporate devices.
  * Emits event bus audit notifications on state changes.

---

### 🔹 **5. KPI Counters & Donut Analytics Alignment (`app.js` & `DashboardService.java`)**
* **Location**: [`app.js`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/resources/webroot/js/motadata/app.js)
* **What It Does**:
  * Fixed numeric type casting in Java (`Number.longValue()`) and array extraction in `app.js` (`res.data.subnets || res.data.items`).
  * Restored live metrics on KPI cards:
    * **Used IPs**: `1,009` (Orange)
    * **Available IPs**: `2,567` (Green)
    * **Total Ping / Failures**: `3,576` Total / `2,567` Failure.
  * Restored **IP Availability Donut** ($71.78\%$ Available / $28.22\%$ Used).
  * Restored **DNS Status Summary** and **Vendor Hardware Distribution** bar charts.

---

### 🔹 **6. Dynamic Category Utilization & Left Panel Navigation (`app.js` & `widget.js`)**
* **Location**: [`widget.js`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/resources/webroot/js/motadata/widget.js#L244-L260)
* **What It Does**:
  * Groups active subnets by `categoryName` rather than `location`, ensuring **Top 10 Category Utilization** matches the **Left Panel Inventory** 1-to-1.
  * Added interactive click event on Category rows:
    * Automatically opens and expands the Left Panel Inventory TreeView.
    * Sets search focus on that category.

---

### 🔹 **7. Role-Based Access Control (RBAC) & Screen Visibility (`top.js`, `AuthHandler.java`, `ip-requests.js`)**
* **Location**: [`top.js`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/resources/webroot/js/motadata/top.js#L14-L35) & [`AuthHandler.java`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/java/com/motadata/ipam/auth/AuthHandler.java#L430-L438)
* **What It Does**:
  * Issued `userRole` cookie on login.
  * Implemented `topManager.applyRoleBasedVisibility()`:
    * **Regular User (`ROLE_USER`)**: Sees only **Home Dashboard**, **IP Requests** (to submit requests), and **Reports**. Admin-only icons (**Settings**, **Rogue Detection**, **Alerts**, **Event Logs**) and modification buttons (**+ Add Subnet**, **Delete**, **Scan**) are hidden.
    * **Administrator (`ROLE_ADMIN`)**: Full access to all screens, IP approval panels, and maintenance controls.

---

### 🔹 **8. IP Range Status Management & Boundary Validation (`SubnetService.java` & `subnet-summary.js`)**
* **Location**: [`SubnetService.java`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/java/com/motadata/ipam/subnet/SubnetService.java#L671-L745) & [`subnet-summary.js`](file:///home/vatsal-rathi/Downloads/IPAM/vertx-server/src/main/resources/webroot/js/motadata/subnet-summary.js#L805-L935)
* **What It Does**:
  * **Strict IP Range Validation**:
    * Verifies `IPv4Util.isValidIpv4()` for start and end IP addresses.
    * Enforces `startIp <= endIp` numeric range validation.
    * Checks boundaries against the subnet's network address and broadcast address, preventing modifying IPs outside the active subnet.
    * Verifies `updateResult.rowCount() > 0`, ensuring only valid ranges return success.
  * **Uppercase Status Standard**:
    * Standardized all IP status representations to uppercase (`AVAILABLE`, `USED`, `TRANSIENT`, `RESERVED`) across database queries, REST API responses, and Kendo UI dropdowns/grids.
  * **Supernet Left Panel Fix**:
    * Populated `subnetName` and empty `subnets: []` array on supernet objects, preventing `undefined` display in the left panel.

---

## 3. Database Schema Overview

| Table Name | Primary Purpose | Key Fields |
| :--- | :--- | :--- |
| `subnets` | Subnet inventory records | `id`, `subnetAddress`, `subnetMask`, `subnetCidr`, `totalIp`, `usedIp`, `availableIp`, `location` |
| `subnet_ip_details` | Individual IP addresses & states | `id`, `subnetId`, `ipAddress`, `macAddress`, `status` (`USED`/`AVAILABLE`/`TRANSIENT`/`RESERVED`), `last_alive_time` |
| `event` | Historical audit logs | `id`, `eventType`, `eventContext`, `severity` (1/2/3), `createdDate`, `userId` |
| `alerts` | Active threshold notifications | `id`, `alertName`, `alertType`, `severity`, `status` (`ACTIVE`/`ACKNOWLEDGED`/`CLEARED`) |
| `discovered_subnets` | Gateway ARP discovered subnets | `id`, `gatewayIp`, `subnetAddress`, `cidr`, `hostCount`, `status` |
| `user` | System user credentials | `id`, `userName`, `password`, `email`, `status`, `userRoleId_id` |
| `user_role` | Role definitions | `id`, `role` (`ADMIN`, `OPERATOR`, `USER`) |
| `role_feature_permission` | Granular feature rights | `id`, `role_id`, `feature_id`, `read_permission`, `write_permission` |

---

## 4. Verification & Testing

* **Compilation**: `mvn test-compile` $\rightarrow$ **`BUILD SUCCESS`** (0 errors).
* **Test Suite**: `mvn test` $\rightarrow$ **`26 Tests Run, 0 Failures, 0 Errors, 0 Skipped`**.

---

## 5. Transient IP State Machine & Lifecycle Management

* **Purpose**: Prevents accidental IP conflicts when devices are temporarily powered down (e.g. employee workstations on weekends) by enforcing a 7-day holding grace period.
* **Transition Rules**:
  * **`AVAILABLE` $\rightarrow$ Responds (`UP`)**: Status transitions to **`USED`**, `last_alive_time = NOW()`.
  * **`USED` $\rightarrow$ Offline (`DOWN`)**: Status transitions to **`TRANSIENT`**, `previous_status = 'USED'`.
  * **`TRANSIENT` $\rightarrow$ Responds (`UP`)**: Status transitions back to **`USED`**, `previous_status = 'TRANSIENT'`.
  * **`TRANSIENT` $\rightarrow$ Remains Offline $\ge$ 7 Days (`DOWN`)**: Status expires to **`AVAILABLE`**, `previous_status = 'TRANSIENT'`.
  * **`RESERVED` $\rightarrow$ Offline (`DOWN`)**: Remains **`RESERVED`**.
* **Aggregation**: Recalculates `used_ip`, `available_ip`, and `transient_ip` counters atomically in `subnet_details` and updates dashboard metrics.

---

## 6. Role-Based IP Requests & Concurrency Protection

* **Role Separation**:
  * **Standard User (`ROLE_USER`)**: Submits requests specifying only count & purpose (`preferredSubnet = false`). Can only view requests submitted by themselves. Cannot approve or decline.
  * **Administrator (`ROLE_ADMIN`)**: Views all requests across the entire organization. Has full action controls (Subnet Dropdown, Available IP Selector, Remark, Approve, and Reject).
* **Real-Time Concurrency Protection**:
  * Before applying approval, `verifyAllIpsAvailable` checks MariaDB in real time.
  * If any selected IP was allocated or discovered as `USED`/`RESERVED` between request creation and admin approval, approval is rejected with an actionable notification to prevent IP collision.
  * Upon successful approval, allocated IPs transition atomically to **`RESERVED`** (`previous_status = 'AVAILABLE'`), change logs are inserted into `ip_change_log`, and parent subnet counters are synced.




