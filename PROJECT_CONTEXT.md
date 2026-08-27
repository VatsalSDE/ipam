# IPAM Migration Master Context & Execution Plan

> **Project:** Motadata TraceOrg IPAM (IP Address Manager) v4.0  
> **Source Stack:** Spring Boot 1.5.9 (Java 8) + MariaDB/MySQL + Python plugins (`pysnmp`, `pywinrm`) + Go Ping Engine  
> **Target Stack:** Eclipse Vert.x (Java 17/21) Async Reactive + MariaDB (`vertx-mysql-client`) + Pure Go Plugins (`go-plugins/`)  
> **Timeline:** 15 Working Days  
> **Migration Rule:** **Non-destructive separate directory approach** (building new Go plugins in `go-plugins/` and Vert.x app in `vertx-server/` without breaking original legacy source code).

---

## 📌 Context Continuity & Coding Protocol (For Every Chat Session)

Whenever a new chat session starts or context needs to be refreshed:
1. **Read this file first (`PROJECT_CONTEXT.md`)** to load the exact project state, architecture, roadmap, and conventions.
2. **Consult the [Progress & Status Tracker](#-progress--status-tracker)** to identify the current milestone and the next immediate task.
3. **Preserve folder separation**: Keep legacy code untouched (`src/`, `python-engine/`); place all new Go plugins in `go-plugins/<plugin-name>/` and Vert.x code in `vertx-server/`.
4. **Mandatory Code Style Rule**: **Every code file must have one empty line spacing between statements, variables, and logic blocks** (spacious, single-spaced statement structure matching company standard).
5. **Update this document**: At the end of every significant step, update the status badges, completed items, and notes in this file.

---

## 🏗️ Architecture & Migration Strategy

### 1. High-Level Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│  Client / UI (REST Client / Web UI)                                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST + JWT Bearer Auth
┌───────────────────────────────────▼────────────────────────────────────┐
│  Target: Vert.x Reactive Server (`vertx-server/`)                      │
│  - Vert.x Web Router (Non-blocking HTTP & API endpoints)              │
│  - JWT & PBAC/RBAC Auth Handlers (replacing Spring OAuth2)             │
│  - Reactive MySQL Pool (`vertx-mysql-client` against `ipam` DB)        │
│  - Worker Verticles / `executeBlocking` for background scheduling      │
│  - Subnet CIDR calculation & IP lifecycle engine                       │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ NuProcess / OS Process Spawn   │ NuProcess / OS Process Spawn
                    ▼                                ▼
    ┌───────────────────────────────┐  ┌─────────────────────────────────┐
    │  `go-engine/ping` (Existing)  │  │  `go-plugins/` (New Pure Go)    │
    │  - Concurrent ICMP bulk ping  │  │  - `snmp-scan` (Route Discovery)│
    │  - Returns {up:[], down:[]}   │  │  - `snmp-arp` (IP-to-MAC Walk)  │
    │                               │  │  - `windows-dhcp` (WinRM/NTLM)  │
    └───────────────────────────────┘  └─────────────────────────────────┘
```

### 2. Spring Boot to Vert.x Mapping

| Spring Boot (Legacy) | Vert.x Target Equivalent | Purpose in IPAM |
| :--- | :--- | :--- |
| `@SpringBootApplication` / `ApplicationOAuth` | `MainVerticle` + `HttpServer` | Application entry point and server startup |
| `@RestController` / `@RequestMapping` | `Router` with route handlers (`router.get(...)`) | REST API layer |
| `@Service` / `@Component` | Plain Java Services / Worker Verticles | Business logic (IP calculation, scan coordinator) |
| `@Repository` / Spring Data JPA | `Pool` / `MySQLPool` (`vertx-mysql-client`) | Non-blocking SQL queries against `ipam` database |
| `TraceOrgOAuth2` / Spring Security | `JWTAuthHandler` + Custom Permission Handler | User authentication and role-based access control |
| Quartz Schedulers (`scheduler/`) | `vertx.setPeriodic()` / Cron Worker Verticles | Recurring scans (subnet ping, SNMP, DHCP, reports) |
| ActiveMQ (`alertQueue`) | Vert.x `EventBus` (internal) / ActiveMQ client | Event alerting and notification streaming |
| `NuProcess` Python execution | Process execution in worker pool | Spawning Go plugin binaries with JSON stdin/args |

### 2.1 Clean Modular Vert.x Package Architecture

vertx-server/src/main/java/com/motadata/ipam/
├── MainVerticle.java             # Lightweight server bootstrapper & lifecycle manager
├── verticle/                     # Background Worker Verticles
│   └── ScanWorkerVerticle.java   # Dedicated worker verticle with EventBus messaging
├── router/                       # Modular route composition layer
│   ├── AppRouter.java            # Master router (global middleware + mounts sub-routers)
│   ├── CorrelationIdHandler.java # Distributed tracing middleware (X-Correlation-ID + MDC)
│   ├── AuthRouter.java           # Login, refresh, logout, me routes
│   ├── SubnetRouter.java         # Subnet & IP address REST endpoints
│   └── HealthRouter.java         # /health and /api/health routes
├── api/                          # HTTP Handlers (controllers)
│   ├── AuthHandler.java          # Authentication endpoints (pure JsonObject)
│   ├── SubnetHandler.java        # Subnet CRUD & IP endpoints (pure JsonObject)
│   ├── ScannerHandler.java       # Subnet scanner endpoints (EventBus dispatch)
│   └── HealthHandler.java        # System diagnostic endpoints
├── service/                      # Reactive Domain Services
│   ├── SubnetService.java        # Bitwise CIDR calculations, overlap detection, batch IP inserts
│   └── ScannerService.java       # Keyset cursor streaming (512 chunks) ping coordinator
├── scheduler/                    # Non-Blocking Background Timers
│   └── SubnetScanScheduler.java  # Vert.x periodic timers replacing legacy Quartz
├── security/                     # Security & Cryptography Subsystem
│   ├── JwtTokenService.java      # HS256 JWT generation & validation (pure JsonObject)
│   ├── RbacAuthHandler.java      # Bearer auth & requirePermission middleware (pure JsonObject)
│   ├── PasswordUtil.java         # PBKDF2 65536-iteration password hashing
│   └── SecurityUtil.java         # IDOR ownership checks, SQL sanitization & security headers
├── database/                     # Reactive Persistence Layer
│   └── DatabasePool.java         # vertx-mysql-client connection pool
├── plugin/                       # Native Binary Process Bridge
│   └── GoPluginBridge.java       # WorkerExecutor OS bridge to go-plugins/bin/ipam-engine
├── util/                         # High-Performance Math Engines
│   └── IPv4Util.java             # O(1) bitwise CIDR math, netmask, host counts, overlap detection
├── model/                        # Domain Constants & Response Envelopes
│   └── ApiResponse.java          # Stateless JSON envelope & 503 backpressure handler
└── config/                       # Centralized Configuration
    └── AppConfig.java            # Singleton YAML & Environment loader
```

### 3. Python to Go Plugins Mapping & Unified Engine

| Legacy Plugin File | Go Package Location | Master CLI Command | Status |
| :--- | :--- | :--- | :--- |
| `go-engine/ping.go` | `go-plugins/pkg/ping/` | `./ipam-engine ping ...` | **Completed** |
| `scansubnet.py` | `go-plugins/pkg/snmpscan/` | `./ipam-engine snmp-scan ...` | **Completed** |
| `remotesubnetdetails.py` | `go-plugins/pkg/snmparp/` | `./ipam-engine snmp-arp ...` | **Completed** |
| `plugin.py` + `winrmclient.py` | `go-plugins/pkg/winrmdhcp/` | `./ipam-engine windows-dhcp ...` | **Completed** |
| **Unified Master Engine** | `go-plugins/cmd/ipam-engine/` | `go-plugins/bin/ipam-engine` | **Completed & Tested** |

---

## 📅 15-Day Migration Roadmap

```
[Day 1-4] Phase 1: Go Plugins Migration & Validation
[Day 5-7] Phase 2: Vert.x Core Foundation, Auth & DB
[Day 8-12] Phase 3: Domain Modules (Subnet, Scan, Discovery, DHCP, Admin)
[Day 13-15] Phase 4: Integration, Testing, Performance & Final Handover
```

### Phase 1: Go Plugins Migration (Days 1 – 4)
- [x] **Day 1: SNMP Gateway Discovery Plugin**
  - Implement `go-plugins/snmp-scan/main.go` using `gosnmp` supporting SNMP v1, v2c, and v3 (MD5/SHA auth, DES/AES privacy).
  - Standardize JSON CLI input and stdout output contract.
- [x] **Day 2: SNMP ARP IP-to-MAC Walk Plugin**
  - Implement `go-plugins/snmp-arp/` to replace `remotesubnetdetails.py`.
  - Perform SNMP walk on `1.3.6.1.2.1.4.22.1.2` (ipNetToMediaPhysAddress) and Cisco OIDs to map IP $\rightarrow$ MAC.
- [x] **Day 3: Windows DHCP WinRM Collector Plugin**
  - Implement `go-plugins/windows-dhcp/` to replace `plugin.py` and `winrmclient.py`.
  - Connect over WinRM (HTTP/HTTPS, NTLM/Kerberos/Basic) and run DHCP management PowerShell cmdlets.
- [x] **Day 4: Go Plugin Integration Testing & Java Bridge Testing**
  - Validate all 4 Go binaries (`ping`, `snmp-scan`, `snmp-arp`, `windows-dhcp`) against test fixtures.
  - Verify seamless execution via Java `NuProcess` / OS Process execution.

### Phase 2: Vert.x Core Foundation & Infrastructure (Days 5 – 7)
- [x] **Day 5: Vert.x Project Setup & DB Layer**
  - Initialized `vertx-server/` with Maven (Java 21 LTS + Vert.x 4.5.8).
  - Wired YAML configuration loader (`config/ipm-conf.yml` & env overrides).
  - Configured reactive `vertx-mysql-client` connection pool and health check endpoint.
- [x] **Day 6: Security, Auth & RBAC Middleware**
  - Implemented `JwtTokenService` (HS256 Bearer tokens, claims, 24h expiration).
  - Implemented `PasswordUtil` with robust PBKDF2/BCrypt constant-time verification.
  - Built `AuthHandler` (`/api/auth/login`, `/api/auth/logout`, `/api/auth/me`) & `RbacAuthHandler` permission middleware.
  - 100% test coverage passed in `AuthHandlerTest`.
- [x] **Day 7: Worker Execution Framework & Process Bridge**
  - Built async `GoPluginBridge` executing Go plugins on dedicated `WorkerExecutor` pool without blocking Vert.x Event Loop.
  - Added enterprise `VertxOptions` and `DeploymentOptions` (multi-core instance scaling & thread pool sizing).
  - Automated integration test passed 100%.

### Phase 3: Business Domain & API Migration (Days 8 – 12)
- [x] **Day 8: Subnet & Supernet Management**
  - Built `IPv4Util` bitwise $O(1)$ CIDR math engine (network/broadcast address, netmask, host counts, overlap detection, 512 batch chunk generator).
  - Built `SubnetService` using native `JsonObject` without POJO bloat (CRUD, overlap detection against DB, batch IP insertion).
  - Built `SubnetHandler` and `SubnetRouter` with granular RBAC permissions (`PERM_SUBNET_VIEW`, `PERM_SUBNET_EDIT`, `PERM_SUBNET_DELETE`).
  - Added `CorrelationIdHandler` for distributed tracing (`X-Correlation-ID`).
  - 100% test coverage passed in `SubnetHandlerTest` and `IPv4UtilTest` (19/19 tests green).
- [x] **Day 9: Subnet Scanner & Periodic Schedulers**
  - Built `ScannerService` coordinating on-demand ICMP ping sweeps via `GoPluginBridge` and batch-updating MariaDB (`AVAILABLE` $\rightarrow$ `USED`).
  - Built `ScannerHandler` exposing `POST /api/subnet/:id/scan` and `GET /api/subnet/:id/scan-status` (plus legacy compatibility route `GET /api/subnet/scan/:id`).
  - Replaced heavy Quartz scheduler with native non-blocking `SubnetScanScheduler` using Vert.x periodic timers (`vertx.setPeriodic`).
  - Wired into `MainVerticle` lifecycle (automated startup & graceful shutdown).
  - 100% test coverage passed (21/21 tests green).
- [ ] **Day 10: Gateway Discovery & DHCP Subsystems**
  - Port Gateway controller & discovery service (triggering `go-plugins/snmp-scan`).
  - Port DHCP credentials management, scope synchronization, and lease utilization tracking.
- [ ] **Day 11: Alerting, Audit Events & Notification Engine**
  - Event logging engine (audit trail for subnet additions, IP state changes, conflicts).
  - Alert trigger rules (utilization threshold, IP conflicts, unauthorized MAC changes) over Vert.x EventBus.
- [ ] **Day 12: Admin Settings, User Management & Reports**
  - User and Role management (PBAC / permissions tree).
  - Mail server configuration, global brand settings, and scheduled report export jobs.

### Phase 4: Integration, Hardening & Final Handover (Days 13 – 15)
- [ ] **Day 13: End-to-End API Parity & DB Verification**
  - Run side-by-side verification comparing Vert.x responses against legacy Spring Boot endpoints.
- [ ] **Day 14: UI Integration & Asset Serving**
  - Configure static file routing / UI proxying, CORS policy, and session handling.
- [ ] **Day 15: Benchmark, Containerization & Sign-Off**
  - Build executable fat JAR (`vertx-ipam.jar`) and multi-stage Docker build.
  - Final documentation and project sign-off.

---

## 📊 Progress & Status Tracker

| Component | Category | Target Location | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Bulk Ping Engine** | Plugin | `go-plugins/pkg/ping/` | `[DONE]` | Thread-safe concurrent Go worker |
| **SNMP Route Discovery** | Plugin | `go-plugins/pkg/snmpscan/` | `[DONE]` | Full v1/v2c/v3 SNMP walk in unified engine |
| **SNMP ARP (IP $\leftrightarrow$ MAC)** | Plugin | `go-plugins/pkg/snmparp/` | `[DONE]` | Replaced `remotesubnetdetails.py` in unified engine |
| **Windows DHCP Collector** | Plugin | `go-plugins/pkg/winrmdhcp/` | `[DONE]` | Replaced `plugin.py` & `winrmclient.py` in unified engine |
| **Unified Master Engine** | Plugin | `go-plugins/cmd/ipam-engine/` | `[DONE]` | Master CLI binary compiled at `go-plugins/bin/ipam-engine` |
| **Vert.x Core & Config** | Vert.x | `vertx-server/` | `[DONE]` | Java 21, YAML config loader, Health check |
| **Database Pool (MySQL)** | Vert.x | `vertx-server/` | `[DONE]` | Reactive `vertx-mysql-client` pool configured |
| **Go Plugin Bridge** | Vert.x | `vertx-server/src/.../plugin/` | `[DONE]` | Dedicated `WorkerExecutor` pool for Go plugins |
| **JWT & RBAC Auth** | Vert.x | `vertx-server/` | `[DONE]` | Pure `JsonObject` HS256 JWT & PBAC permissions |
| **Subnet & IP APIs** | Vert.x | `vertx-server/` | `[DONE]` | O(1) CIDR math, overlap detection, batch IP inserts |
| **Scan Coordinator** | Vert.x | `vertx-server/` | `[DONE]` | Async Go ping execution & Vert.x periodic timers |
| **Discovery & DHCP APIs** | Vert.x | `vertx-server/` | `[PLANNED - Next]` | Gateway & DHCP scope controllers |
| **Alerts & EventBus** | Vert.x | `vertx-server/` | `[PLANNED]` | In-memory reactive event streaming |
| **Settings & Reports** | Vert.x | `vertx-server/` | `[PLANNED]` | User/Role, Brand, Mail, DB Maintenance |

---

## 🔌 Plugin JSON Contracts & Interfaces

### 1. SNMP Route Discovery Plugin (`go-plugins/snmp-scan`)
- **Input (CLI Argument or STDIN):**
  ```json
  {
    "gateway": "192.168.1.1",
    "port": 161,
    "version": "v2c",
    "community": "public",
    "timeout": 5,
    "retries": 1
  }
  ```
- **Output (STDOUT):**
  ```json
  {
    "result": {
      "192.168.1.0": "255.255.255.0",
      "10.0.0.0": "255.0.0.0"
    }
  }
  ```
- **Error Output:**
  ```json
  {
    "error-code": "CONNECT_FAILED: timeout"
  }
  ```

### 2. SNMP ARP Table Walk Plugin (`go-plugins/snmp-arp`)
- **Input (CLI Argument or STDIN):**
  ```json
  {
    "gateway": "192.168.1.1",
    "port": 161,
    "version": "v2c",
    "community": "public",
    "subnet_ip": "192.168.1.0",
    "cidr": "24",
    "timeout": 5,
    "retries": 1
  }
  ```
- **Output (STDOUT):**
  ```json
  {
    "result": [
      {
        "ip": "192.168.1.10",
        "mac": "00:50:56:C0:00:08",
        "interface": "eth0",
        "type": "dynamic"
      }
    ]
  }
  ```

### 3. Windows DHCP Collector Plugin (`go-plugins/windows-dhcp`)
- **Input:**
  ```json
  {
    "host": "192.168.1.50",
    "port": 5985,
    "username": "Administrator",
    "password": "Password123",
    "auth_type": "ntlm",
    "action": "collector",
    "scope_ids": ["192.168.1.0"]
  }
  ```
- **Output:**
  ```json
  {
    "result": {
      "scopes": [
        {
          "scope_id": "192.168.1.0",
          "name": "Default Scope",
          "subnet_mask": "255.255.255.0",
          "start_range": "192.168.1.100",
          "end_range": "192.168.1.200",
          "leases": [
            {
              "ip": "192.168.1.105",
              "mac": "00:1A:2B:3C:4D:5E",
              "hostname": "WORKSTATION-01",
              "lease_expiry": "2026-08-27 10:00:00"
            }
          ]
        }
      ]
    }
  }
  ```

---

## 🛠️ Configuration & Database Reference

- **Config File:** `config/ipm-conf.yml`
  - `server-port`: Application listening port (default: 8080)
  - `db-host`, `db-port`: MariaDB/MySQL connection endpoint
  - `db-pool-max-size`: Max active connections in MySQLPool (default: 50)
  - `db-pool-max-wait-queue`: Max waiting queries before saturation backoff (default: 500)
  - `max-ping-check-timeout`, `max-ping-check-retry-count`, `max-concurrent-ping`
- **Database Schema:** `ipam`
- **Migration Scripts:** Located in `src/main/resources/db/migration/` (`V2.*` to `V4.*` SQL scripts)

---

## 📝 Change Log & Session Records

| Date | Changes Made | Next Action |
| :--- | :--- | :--- |
| **Day 1 (2026-08-26)** | Created `go-plugins/snmp-scan` with full SNMP v1/v2c/v3 support replacing `scansubnet.py`. Created master context & 15-day roadmap file (`PROJECT_CONTEXT.md`). | Implement `go-plugins/snmp-arp` to replace `remotesubnetdetails.py`. |
| **Day 2 (2026-08-26)** | Created & tested `go-plugins/snmp-arp` (RFC1213 & IP-MIB ARP walk, MAC formatter, CIDR filter) replacing `remotesubnetdetails.py`. | Implement `go-plugins/windows-dhcp` (WinRM / PowerShell DHCP collector). |
| **Day 3-4 (2026-08-26)** | Created & tested `go-plugins/windows-dhcp` replacing `plugin.py` & `winrmclient.py`. Restructured Go plugins to standard layout with unified `ipam-engine` master CLI. Cleaned up temporary standalone folders. **Phase 1 COMPLETED**. | Begin Phase 2: Scaffold `vertx-server/` core, YAML config, and MySQL pool. |
| **Day 5 (2026-08-26)** | Scaffolded `vertx-server/` (Java 21 LTS + Vert.x 4.5.8), YAML config loader (`AppConfig`), Reactive MySQL Pool (`DatabasePool`), and Health Check handler (`HealthHandler`). Automated integration test passed 100%. | Day 6: Implement JWT Authentication & RBAC Permission Filter. |
| **Day 5.5 (2026-08-26)** | Implemented Strategy 5 (centralized error dispatcher with HTTP 503 / `Retry-After` on pool saturation) in `ApiResponse` & `MainVerticle`. Added dynamic pool sizing (`db-pool-max-size: 50`, `db-pool-max-wait-queue: 500`) in `AppConfig` and `DatabasePool`. 100% tests passed. | Day 6: Implement JWT Authentication & RBAC Permission Filter. |
| **Day 6 (2026-08-26)** | Implemented `JwtTokenService` (HS256 Bearer + 7-day Refresh tokens), `PasswordUtil` (PBKDF2), `AuthHandler` (`/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/me`), and `RbacAuthHandler` middleware with `TOKEN_EXPIRED` & `REFRESH_TOKEN_EXPIRED` interceptor support. Phase 2 COMPLETED (8/8 tests passed 100%). | Phase 3 / Day 8: Implement Subnet & Supernet Domain Management APIs. |
