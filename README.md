# 🏭 Smart Warehouse – Hệ thống quản lý kho hàng thông minh

A full-stack IoT warehouse management system combining **ESP32 hardware**, **Spring Boot backend**, and a **React dashboard** to automate inventory tracking, environmental monitoring, and stock reconciliation.

---

## 📋 Project Status

> **Phase: Active Development**

| Component | Status |
|---|---|
| 📐 System Architecture | ✅ Done |
| 🗄️ Database Schema | ✅ Done (`V1__initial_database.sql`) |
| ⚙️ Backend (Spring Boot) | 🔨 In Progress |
| 🖥️ Frontend (React + Vite) | 🔨 In Progress |
| 📡 Hardware (ESP32) | 🔨 In Progress |
| 🐳 Docker Compose | 📋 Planned |

---

## 🏗️ System Architecture

```
┌─────────────┐     ┌───────────┐     ┌──────────────────────┐     ┌────────────────────┐
│   Sensors   │ --> │   ESP32   │ --> │  Backend + Database   │ --> │ Frontend Dashboard │
│ (DHT22,     │     │ (Process  │     │ (Spring Boot + MySQL  │     │  (React + Vite     │
│  MQ-2,      │     │  & send   │     │  + Redis + RabbitMQ) │     │  web dashboard)    │
│  RC522,     │     │  via MQTT │     │                       │     │                    │
│  HX711)     │     │  / HTTP)  │     │                       │     │                    │
└─────────────┘     └───────────┘     └──────────────────────┘     └────────────────────┘
       │                   │
       │                   └──> On-site output: Fan (relay), Buzzer, LED, OLED display
       │
       └──> External alerts: Telegram / Zalo / Email
```

**Data flow:** `Sensor → ESP32 → Backend/DB → Frontend (real-time dashboard)`

---

## ✨ Features

### Feature 1 – Environmental Monitoring
- **Temperature & Humidity** monitoring (DHT22) with automatic fan activation via relay
- **Smoke / Gas / Fire detection** (MQ-2) with buzzer + LED alarm
- Real-time data displayed on the web dashboard
- Configurable alert thresholds; notifications via Telegram/Zalo/Email

### Feature 2 – RFID Inventory Tracking
- Two separate RC522 readers — one for **stock IN**, one for **stock OUT**
- Scan RFID tag → automatically records the transaction to the database
- LED feedback: 🟢 Green = success, 🔴 Red = unknown product / error
- Optional OLED display showing SKU and product name on scan

### Feature 3 – Load Cell Stock Reconciliation
- Load cell (HX711) weighs total shelf load → calculates actual quantity
- Cross-checks against system quantity (recorded by Feature 2)
- Discrepancy detected → triggers 🔴 Red LED + system alert
- Measurement settled after a 3-second stabilization delay to filter out vibration noise
- Configurable check frequency (e.g., every 10 minutes or end-of-day)

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Spring Boot 4.1 · Java 17 |
| **Frontend** | React 19 · Vite 8 · Ant Design 6 · Redux Toolkit |
| **Database** | MySQL 8.0 |
| **DB Migration** | Flyway |
| **Cache** | Redis |
| **Message Queue** | RabbitMQ |
| **Security** | Spring Security · JWT (OAuth2 Resource Server) |
| **Realtime** | WebSocket (STOMP) |
| **HTTP Client** | Spring Cloud OpenFeign |
| **Email** | Spring Mail |
| **Hardware** | ESP32 · DHT22 · MQ-2 · RC522 (×2) · HX711 · Load Cell 5kg |

---

## 📁 Project Structure

```
Smart_Warehouse/
├── backend/
│   └── iot/                         # Spring Boot application
│       ├── src/main/java/com/example/iot/
│       │   ├── config/              # Security, RabbitMQ, Redis configs
│       │   ├── controller/          # REST API controllers
│       │   ├── dto/                 # Request / Response DTOs
│       │   ├── entity/              # JPA entities
│       │   ├── enums/               # Shared enumerations
│       │   ├── exception/           # Global exception handling
│       │   ├── mapper/              # MapStruct mappers
│       │   ├── repository/          # Spring Data JPA repositories
│       │   └── service/             # Business logic layer
│       └── src/main/resources/
│           ├── application.properties
│           └── db/migration/
│               └── V1__initial_database.sql   # Flyway initial schema
├── frontend/                        # React + Vite application
│   ├── src/
│   ├── package.json
│   └── vite.config.js
└── docs/
    └── architecture.md              # Detailed system design document
```

---

## 🚀 Getting Started

### Prerequisites

Make sure the following are installed on your machine:

| Tool | Version | Download |
|---|---|---|
| Java JDK | 17+ | [adoptium.net](https://adoptium.net) |
| Maven | 3.9+ | Bundled (`mvnw`) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org) |
| MySQL | 8.0 | [mysql.com](https://dev.mysql.com/downloads/) |

### 1. Clone the repository

```bash
git clone https://github.com/VuQuocHiep/Smart_Warehouse
cd Smart_Warehouse
```

### 2. Configure environment variables

Set the following environment variables in the terminal that starts the backend
(a `.env` file is not loaded automatically):

```bash
DB_USERNAME=root
DB_PASSWORD=your_mysql_password
```

The configured database is **`iot`** on `localhost:3306`. Create the empty database
in MySQL Workbench before starting the backend:

```sql
CREATE DATABASE IF NOT EXISTS iot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

On Windows PowerShell, set credentials in the same terminal:

```powershell
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'your_mysql_password'
```

### 3. Run the Backend

```bash
cd backend/iot
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

On a fresh database, Flyway runs `B1__jpa_initial_schema.sql`, followed by V2–V7.
The baseline creates the UUID-based tables used by the current JPA entities;
later migrations add monitoring tables and four demo warehouses. Hibernate then
validates the schema. No manual SQL import is needed.

Do not manually run the legacy `V1__initial_database.sql`: it switches to
`smart_warehouse`, drops that database, and creates an older hardware schema
that does not match the current entities. The file is retained unchanged for
existing Flyway histories. Existing databases do not run the new B1 baseline.

If an earlier startup recorded a failed V2 migration, restarting or running
Flyway `repair` alone will not create the missing base tables. For a **confirmed
disposable development database with no data to keep**, stop the backend and
recreate only `iot` in MySQL Workbench before restarting:

```sql
DROP DATABASE IF EXISTS iot;
CREATE DATABASE iot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

This deletes everything in `iot`, including the failed migration history. For a
database containing data, back it up and reconcile the schema before repairing
the history; do not use this reset. Automatic baselining of nonempty databases
is disabled so incomplete schemas cannot silently skip initialization.

The backend will be available at: `http://localhost:8080`

### 4. Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend will be available at: `http://localhost:5173`

---

## 🗄️ Database Migrations (Flyway)

Migration scripts live at:

```
backend/iot/src/main/resources/db/migration/
├── B1__jpa_initial_schema.sql  ← Current base schema for fresh databases
├── V1__initial_database.sql    ← Legacy schema; do not import manually
└── V2__....sql                 ← Monitoring migrations applied after B1
```

**Rules for the team:**

1. ❌ **Never edit** an existing migration file that is already committed to Git.
2. ✅ To add a table or column, create a **new versioned file**:
   ```
   V2__add_notification_table.sql
   V3__add_column_shelf_status.sql
   ```
3. Run the app — Flyway auto-applies only the new scripts.

To verify startup migrations against MySQL, set `MYSQL_TEST_URL` (for example
`jdbc:mysql://localhost:3306/mysql`), `MYSQL_TEST_USER` and `MYSQL_TEST_PASSWORD`,
then run `./mvnw test -Dtest=MySqlBootstrapTest` (`.\mvnw.cmd` on PowerShell).
The user must be allowed to create/drop databases. This test creates a uniquely
named temporary schema, applies all migrations, validates every JPA entity,
checks warehouse seeds and verifies a second migration makes no changes. It
drops only its temporary schema. Without `MYSQL_TEST_URL`, the test is skipped.

---

## 📡 Hardware Components

| Component | Role | Feature |
|---|---|---|
| ESP32 | Main microcontroller | All |
| DHT22 | Temperature + Humidity sensor | Feature 1 |
| MQ-2 | Smoke / Gas sensor | Feature 1 |
| Relay / MOSFET | Fan / buzzer control | Feature 1 |
| RC522 (×2) | RFID reader (IN gate + OUT gate) | Feature 2 |
| RFID Tag / Card (13.56 MHz) | Product identification | Feature 2 |
| HX711 + Load Cell 5kg | Shelf weight measurement | Feature 3 |
| OLED 0.96" I2C | On-site display (SKU, qty, status) | Feature 2 & 3 |
| LED (Red / Green) | Visual status indicator | All |
| Buzzer | Audio alarm | Feature 1 |

---

## 📄 Documentation

- [System Architecture](docs/architecture.md) — Full feature specs, hardware list, data flow diagrams
