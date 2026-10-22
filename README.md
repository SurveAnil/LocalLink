# LocalLink

LocalLink is a browser-based room sharing application with a Spring Boot API for room membership, authenticated messaging, and file transfers.

## Requirements

- Java 25
- Node.js 22 or later (for the frontend test suite)
- Python 3 (to serve the static frontend locally)

## Start the backend

From the project root, run:

```powershell
Push-Location backend
.\mvnw.cmd spring-boot:run
Pop-Location
```

The backend listens on port `8080` locally. In deployment it uses the platform-provided `PORT` value. The default `local` profile uses an in-memory H2 database. To run with PostgreSQL, configure `DB_URL` or `DB_HOST`, `DB_PORT`, and `DB_NAME`, plus `DB_USERNAME` and `DB_PASSWORD`, and start with `SPRING_PROFILES_ACTIVE=cloud`.

## Start the frontend

In a second PowerShell terminal, serve the static source directory:

```powershell
python -m http.server 5500 --directory frontend\src
```

Open [http://localhost:5500/pages/Landing%20page/landing_page.html](http://localhost:5500/pages/Landing%20page/landing_page.html). Locally, the frontend derives the backend host from the page URL and connects to port `8080`.

## Run tests

Backend tests:

```powershell
Push-Location backend
.\mvnw.cmd verify
Pop-Location
```

Frontend tests:

```powershell
node --test frontend\tests\*.test.cjs
```

The frontend has no package installation or bundling step; it uses browser-native JavaScript and locally bundled QR libraries.

## Project layout

- `backend\src\main\java` — room, session, realtime, chat, and file-transfer API.
- `backend\src\main\resources\db\migration` — Flyway schema migrations.
- `backend\src\test\java` — backend integration and authorization tests.
- `frontend\src\pages`, `frontend\src\scripts`, and `frontend\src\styles` — static UI pages and browser logic.
- `frontend\tests` — Node-based tests for connectivity and dashboard behavior.

Configuration is externalized through Spring properties and environment variables. Local database contents and uploaded files are runtime data and are not committed.

## Deploy to Render and Vercel

### Backend on Render

Create the Render Web Service from the repository using the included [`render.yaml`](render.yaml), or configure it manually:

- Runtime: Docker
- Dockerfile: `backend/Dockerfile`
- Docker context: repository root
- Health check path: `/health`
- Instance: a non-sleeping plan with a persistent disk mounted at `/var/data/locallink`

The Blueprint expects a PostgreSQL database to be created in Render. Set `SPRING_PROFILES_ACTIVE=cloud`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` from that database's **internal** connection details. Store the password only in Render's environment settings. Set `LOCALLINK_CORS_ALLOWED_ORIGINS` to the exact Vercel site origin (for example, `https://your-project.vercel.app`), without a trailing slash. Do not use a wildcard for a public deployment.

The service uses Render's `PORT` setting and keeps uploaded files on the configured disk. The included `starter` service plan and persistent disk are paid Render resources; a health endpoint alone does not prevent free instances from sleeping. Persistent uploads require a disk and do not survive disk deletion, so configure backups appropriate to your data.

`GET /health` is unauthenticated and returns plain-text `OK` when the application process is responding. It is a process liveness check, not a database readiness check.

### Frontend on Vercel

Import the repository into Vercel and set the project Root Directory to `frontend`. The included [`vercel.json`](frontend/vercel.json) runs the dependency-free static build. Set this Vercel environment variable for Production and Preview:

```text
LOCALLINK_API_BASE_URL=https://your-render-service.onrender.com/api/v1/rooms
```

Use the actual HTTPS URL assigned to the Render service. Vercel generates the browser configuration during build; the API URL is public client configuration, not a secret. Redeploy after changing it. REST and WebSocket traffic go directly from the browser to Render, so the Render CORS origin must match the deployed Vercel origin.
