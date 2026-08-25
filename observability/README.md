# Observability stack — Prometheus, Grafana, Tempo

Local stack that lights up all **three pillars of observability** for the BookMyShow backend.

## The three pillars → what serves each here

| Pillar | Question it answers | Served by |
|--------|--------------------|-----------|
| **Metrics** | "Is the system healthy? What are the rates/latencies/error ratios?" | Micrometer → `/actuator/prometheus` → **Prometheus** (stored) → **Grafana** (visualized) |
| **Logs** | "What exactly happened for this one request?" | SLF4J + Logback `LogstashEncoder` (JSON) with a `correlationId` + `traceId`/`spanId` in the MDC — ship to ELK/Loki |
| **Traces** | "Where did the time go across services/spans?" | Micrometer Tracing → OTLP → **Grafana Tempo** (query via Grafana Explore) |

## Run it

1. **Start the app with JSON logging** (so logs are aggregation-ready) and OTLP export on:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=json
   # or, on a built jar:
   java -jar target/book-my-show-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=json
   ```

   Verify metrics are exposed: `curl localhost:8080/actuator/prometheus | head`.

2. **Start the observability stack:**

   ```bash
   docker compose -f observability/docker-compose.observability.yml up -d
   ```

3. **Open the tools:**
   - **Grafana** — http://localhost:3000 (login `admin` / `admin`). The *BookMyShow — Observability* dashboard is auto-provisioned (request rate, p95/p99 latency, error rate, JVM heap, GC pause, and the custom booking counters).
   - **Prometheus** — http://localhost:9090 (try the query `rate(bookings_confirmed_total[5m])` or check *Status → Targets* to confirm the app is being scraped).
   - **Tempo** — no standalone UI; open **Grafana → Explore → Tempo** and search a trace by its `traceId` (the same id that appears in the JSON logs, closing the loop between logs and traces).

## How the pieces connect

- Prometheus **pulls** `host.docker.internal:8080/actuator/prometheus` every 15s (see `prometheus.yml`). In Kubernetes you'd use a `ServiceMonitor` instead of a static target — noted in that file.
- The app **pushes** spans over OTLP/HTTP to Tempo at `:4318` (configured via `management.otlp.tracing.endpoint` in `application.yml`).
- Grafana is provisioned with both datasources (`grafana/datasources.yml`) and the dashboard (`grafana/dashboards.yml` + `dashboard-bookmyshow.json`).

Tear down: `docker compose -f observability/docker-compose.observability.yml down -v`.
