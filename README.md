# Green Campus Resource Management System

A manual-entry waste management application for university demonstrations. It uses **Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway, MySQL, Bootstrap 5, and Chart.js**. It has no AI, predictive features, sensors, or IoT components.

## Architecture

- **Browser UI:** separate source in [`frontend/`](frontend/), packaged into Spring Boot's static resources by Maven; responsive Bootstrap pages use Chart.js dashboards and accessible forms.
- **REST API:** Spring Boot controllers and transactional service layer. Session authentication is protected with CSRF tokens; passwords use BCrypt; API access is authorized by role.
- **Persistence:** MySQL schema migrated with Flyway; JPA repositories; constraints, foreign keys, indexes, collection history, and audit logs.
- **Data flow:** staff enter categorized, unit-specific records → API validates category/location/quantity → MySQL stores the record → dashboard and filtered report APIs query stored records. Collection and authorized handover are separate, timestamped events.

## Page structure

Dashboard; Waste Records; Segregation Management; Collection Tracking; E-waste Inventory; Reports; Campus Locations; Environmental Awareness; Alerts; User Management; Profile and Settings. Login is shown before the authenticated application. Role checks are enforced by the backend as well as the UI.

## Requirements

- Java 21
- Maven 3.9+
- MySQL 8.0+

Maven is not installed system-wide in the authoring environment. The test run used a temporary Maven 3.9.9 download that was removed afterward. Install Maven locally to build and run the project as described below.

## Deploy on Render

The repository includes a Render Blueprint in [`render.yaml`](render.yaml). In Render, create a Blueprint from this GitHub repository and set the five prompted environment variables for the web service:

- `DB_URL`: the JDBC URL for a publicly reachable MySQL database, including its required TLS options
- `DB_USERNAME` and `DB_PASSWORD`: the database credentials
- `CAMPUS_ADMIN_USERNAME` and `CAMPUS_ADMIN_PASSWORD`: the initial administrator credentials; the password must be at least 12 characters

Enter credentials directly in Render's environment-variable form; do not commit them to the repository or put them in `render.yaml`. The Render service uses the repository-root Docker build context and the lowercase `dockerfile` path. Ensure the database allows connections from the Render service before deploying. The service listens on Render's `PORT` and uses `/` for its health check.

## Configure and run

1. Create a MySQL database and account, for example:

   ```sql
   CREATE DATABASE green_campus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'green_campus'@'localhost' IDENTIFIED BY 'use-a-local-secret';
   GRANT ALL PRIVILEGES ON green_campus.* TO 'green_campus'@'localhost';
   ```

2. Set environment variables in the terminal that will run Spring Boot. Copy values from [`database/.env.example`](database/.env.example) as a reference; do not commit real secrets.

   PowerShell example:

   ```powershell
   $env:DB_URL = "jdbc:mysql://localhost:3306/green_campus?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   $env:DB_USERNAME = "green_campus"
   $env:DB_PASSWORD = "your-local-database-password"
   $env:CAMPUS_ADMIN_USERNAME = "admin"
   $env:CAMPUS_ADMIN_PASSWORD = "replace-with-a-long-random-password"
   $env:COLLECTION_OVERDUE_DAYS = "7"
   $env:COLLECTION_STATUS_STALE_DAYS = "2"
   $env:EWASTE_HANDOVER_OVERDUE_DAYS = "14"
   ```

   The initial administrator is created on first startup and the password is BCrypt-hashed. Startup fails if the administrator settings are missing or the configured password is shorter than 12 characters.

3. Run from the project root:

   ```powershell
   mvn -f backend/pom.xml spring-boot:run
   ```

4. Open `http://localhost:8080`. Flyway applies `V1__initial_schema.sql` and `V2__ewaste_users_alerts_reports.sql` automatically. The app deliberately creates no unverified campus buildings or fabricated waste data. Add active locations through the administrator account before entering waste records.

The frontend is plain HTML/CSS/JavaScript in `frontend/`; Maven copies it into the backend artifact at build time, so the application serves both the UI and API from the same origin.

For production, use HTTPS, a managed secrets store, least-privilege database credentials, backups, and institution-approved privacy and retention policies.

## API overview

All authenticated API endpoints use JSON unless otherwise noted. Login first obtains a CSRF token from `GET /api/auth/csrf`, then posts credentials to `POST /api/auth/login`. The frontend does this automatically. Logout is `POST /api/auth/logout`.

| Endpoint | Purpose | Access |
|---|---|---|
| `GET /api/auth/me` | Current user | Authenticated |
| `GET /api/dashboard` | DB-derived count and category/status totals | Authenticated |
| `GET /api/records` | Search/filter/page records (`q`, `category`, `status`, `locationId`, `page`, `size`, `sort`) | Authenticated |
| `POST /api/records`, `PUT /api/records/{id}` | Create/update waste record | Admin, Staff |
| `DELETE /api/records/{id}` | Soft deactivate record | Admin |
| `POST /api/records/{id}/schedule` | Schedule collection | Admin, Staff |
| `POST /api/records/{id}/collections` | Record a partial/full collection amount | Admin, Staff |
| `POST /api/records/{id}/handovers` | Record handover and authorized recipient separately | Admin, Staff |
| `GET /api/records/{id}/history` | Timestamped collection/handover history | Authenticated |
| `/api/ewaste` | Dedicated e-waste inventory with item condition, storage, and full-handover details | Authenticated / Admin or Staff writes |
| `/api/locations` | List locations; admin can create/update/deactivate | Authenticated / Admin writes |
| `GET /api/alerts`, `POST /api/alerts/{id}/resolve` | Rule-based overdue alerts and resolution | Authenticated / Admin or Staff resolve |
| `GET /api/reports/summary` | Filtered, paginated record list and category/month/location aggregates | Authenticated |
| `GET /api/reports.csv` | Filtered CSV export (`from`, `to`, `category`, `status`, `locationId`, `segregationStatus`, `itemType`) | Authenticated |
| `/api/users` | Admin creates accounts and changes roles/access; passwords are never returned | Admin |
| `GET /api/audit-logs` | Paginated change history | Admin |
| `GET /api/awareness` | Educational guidance | Public |

Request shapes:

```json
{
  "category": "PLASTIC",
  "subtype": "Plastic bottles",
  "quantity": 12.5,
  "unit": "KG",
  "locationId": "active-location-uuid",
  "recordedAt": "2026-10-22",
  "segregationStatus": "NOT_VERIFIED",
  "notes": "",
  "demo": false
}
```

Collection and handover requests use `{"quantity": 2.5, "notes": "..."}`; handover additionally requires `recipient`. Plastic records use kg; e-waste records use item counts. No cross-unit totals or inferred environmental impact calculations are performed.

Dedicated e-waste item requests use an integer item count, for example:

```json
{
  "itemType": "Keyboard",
  "quantity": 4,
  "locationId": "active-location-uuid",
  "reportedAt": "2026-10-22",
  "condition": "DAMAGED",
  "storageLocation": "Facilities storage area A",
  "segregationStatus": "NOT_VERIFIED",
  "notes": "",
  "demo": false
}
```

## Data and integrity notes

- Collection never implies recycling. An explicit handover event and recipient are required.
- Partial collection leaves the remaining quantity pending. Handover quantity cannot exceed collected waste not already handed over.
- Deactivation preserves records and history. Deactivated locations remain associated with past records but cannot be selected for new records.
- Demo data must be explicitly marked; no real campus data is bundled.
- Dashboard and report totals preserve units and are calculated from active database records; monthly and location charts use only filtered measurements.
- `collection_history` is the event history. `audit_logs` stores important record operations.
- Rule alerts include overdue collection, unchanged scheduled collection, segregation correction, e-waste awaiting authorized handover, and older e-waste records missing condition/storage details. Thresholds use `COLLECTION_OVERDUE_DAYS` (default 7), `COLLECTION_STATUS_STALE_DAYS` (default 2), and `EWASTE_HANDOVER_OVERDUE_DAYS` (default 14). Alerts are generated as deterministic in-app rules when the dashboard/alerts endpoint is read.

## Tests and current scope

Unit tests cover partial collection, separate e-waste condition/storage details, role-gated user access, unauthenticated access, viewer write denial, and safe user response payloads. Run them from the project root with `mvn -f backend/pom.xml test`. The final test run reported **8 tests, 0 failures, 0 errors**. The application itself was not started because a MySQL instance is not available in the environment. The app expects a MySQL database and bootstrap-admin environment variables before startup.

The MVP includes login, role-managed accounts, record/location management, detailed e-waste inventory, collection and handover tracking, database-backed dashboard and filtered report summaries, CSV export, configurable rule thresholds, paginated audit history, and educational content. Alerts are conventional deterministic rules; they are shown in-app and do not send email or SMS.
