# IBM MQ Explorer — Web Console

A production-style web-based IBM MQ Explorer built with **Java 21 + Spring Boot 3.3** (backend)
and **React 18 + Vite + TypeScript** (frontend).

## Features

- **MQ Connection Management**: Save multiple IBM MQ connection profiles (encrypted at rest)
- **Queue Listing**: Browse all local queues with depth, open counts, descriptions via PCF
- **Message Browsing**: Non-destructive browse of messages using `MQGMO_BROWSE`
- **Message Viewer**: Text / JSON (pretty-printed) / XML (pretty-printed) / Hex views
- **Security**: Spring Security form login, ADMIN and VIEWER roles
- **Dark / Light Mode**: Persisted per browser
- **Audit Log**: All connect/disconnect/view actions recorded to database

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.8+ |
| Node.js | 18+ (for frontend dev) |
| IBM MQ | Any version with client access on port 1414 |

## IBM MQ Client Dependency

The IBM MQ Java client (`com.ibm.mq.jakarta.client`) is published to **Maven Central**.

> **Important:** This project uses `com.ibm.mq.jakarta.client` (Jakarta EE namespace), **not**
> `com.ibm.mq.allclient`. Spring Boot 3.x requires the Jakarta namespace.

The dependency is declared in `pom.xml` and resolves automatically:

```xml
<dependency>
    <groupId>com.ibm.mq</groupId>
    <artifactId>com.ibm.mq.jakarta.client</artifactId>
    <version>9.4.1.0</version>
</dependency>
```

If your organization proxies Maven Central, ensure `repo.maven.apache.org` and
`repo1.maven.org` are accessible, or mirror the IBM MQ artifacts to your internal Nexus/Artifactory.

## Quick Start

### 1. Build and Run (with frontend)

```bash
# Full build including React frontend
mvn clean package -DskipTests

# Run
java -jar target/ibm-mq-explorer-1.0.0-SNAPSHOT.jar
```

Open: http://localhost:8080

Default credentials:
- `admin` / `admin123` (ADMIN role)
- `viewer` / `viewer123` (VIEWER role)

### 2. Backend-Only Development

```bash
# Skip frontend build
mvn spring-boot:run -Pbackend-only
```

### 3. Frontend Development (Hot Reload)

```bash
# Terminal 1: start backend
mvn spring-boot:run -Pbackend-only

# Terminal 2: start frontend dev server
cd frontend
npm install
npm run dev
```

Open: http://localhost:3000 (proxied to backend on port 8080)

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MQ_ENCRYPTION_KEY` | `DefaultDevKey32CharsChange!!` | AES-256 key for encrypting stored passwords. **Change in production!** |
| `ADMIN_USERNAME` | `admin` | Admin user name |
| `ADMIN_PASSWORD` | `admin123` | Admin password. **Change in production!** |
| `VIEWER_USERNAME` | `viewer` | Viewer user name |
| `VIEWER_PASSWORD` | `viewer123` | Viewer password |
| `SERVER_PORT` | `8080` | HTTP server port |

### Using MySQL Instead of H2

```bash
# Start with MySQL profile
java -jar target/*.jar --spring.profiles.active=mysql \
  -e MYSQL_HOST=localhost \
  -e MYSQL_DB=mqexplorer \
  -e MYSQL_USER=mquser \
  -e MYSQL_PASSWORD=secret
```

Or set `SPRING_PROFILES_ACTIVE=mysql` environment variable.

## IBM MQ Server Requirements

For queue listing via PCF commands, the IBM MQ server must have:

```mqsc
DEFINE CHANNEL(SYSTEM.ADMIN.SVRCONN) CHLTYPE(SVRCONN) TRPTYPE(TCP)
SET CHLAUTH(SYSTEM.ADMIN.SVRCONN) TYPE(BLOCKUSER) USERLIST('nobody') ACTION(REPLACE)
SET AUTHREC PROFILE('SYSTEM.ADMIN.COMMAND.QUEUE') OBJTYPE(QUEUE) PRINCIPAL('mquser') AUTHADD(DSP, INQ, PUT)
SET AUTHREC PROFILE('**') OBJTYPE(QUEUE) PRINCIPAL('mquser') AUTHADD(BROWSE)
REFRESH SECURITY
```

For basic connection (no PCF), a standard SVRCONN channel is sufficient.

## API Reference

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/login` | Public | Login |
| `POST` | `/api/auth/logout` | Authenticated | Logout |
| `GET` | `/api/mq/health` | Public | Health check |
| `POST` | `/api/mq/config/save` | ADMIN | Save MQ configuration |
| `GET` | `/api/mq/config/all` | All | List configurations |
| `DELETE` | `/api/mq/config/{id}` | ADMIN | Delete configuration |
| `POST` | `/api/mq/connect?configId=N` | All | Connect to queue manager |
| `POST` | `/api/mq/disconnect?configId=N` | All | Disconnect |
| `POST` | `/api/mq/test-connection` | ADMIN | Test connection (no save) |
| `GET` | `/api/mq/queues?configId=N` | All | List queues |
| `GET` | `/api/mq/queues/{name}/messages?configId=N` | All | Browse messages |
| `GET` | `/api/mq/queues/{name}/messages/{id}?configId=N` | All | Get message detail |
| `GET` | `/api/mq/audit` | ADMIN | Recent audit log |

## Security Notes

- Stored passwords are encrypted with AES-256-GCM
- Passwords are never logged or returned in API responses
- Use `MQ_ENCRYPTION_KEY` env var with a strong 32+ character secret in production
- Enable HTTPS via `server.ssl.*` properties or use a reverse proxy (nginx/Traefik)
- The H2 console (`/h2-console`) is restricted to ADMIN role

## Future Enhancements

- Put message capability (ADMIN only, with confirmation)
- Delete/purge message (ADMIN only)
- MQ topic browsing
- Multiple simultaneous queue manager connections
- Real-time queue depth monitoring (WebSocket)
- LDAP/Active Directory integration for user management
- Export messages to CSV / JSON file
- Message routing and dead-letter queue analysis
- Kubernetes/Docker deployment config
