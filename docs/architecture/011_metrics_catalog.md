# 011: Metrics Catalog

## 📋 Purpose
Defines the runtime metrics emitted by TicketLedger for booking reliability, async safety, and platform diagnosis.

## 📊 Naming Convention
- **Business metrics:** `business.<domain>.<action>`
- **Platform metrics:** `async.<domain>.<action>` (and Actuator/JVM defaults)
- **Tags:** Low-cardinality only; never include user-level identifiers.

## 🧭 Shared Dimension: `theater_id`

`MetricAspect` tags business metrics with theater scope from `RequestContext`.

| Value | Meaning |
| :--- | :--- |
| `<theater-uuid>` | Request is scoped to a specific theater. |
| `--global` | Request has no theater context (e.g., auth/system paths). |

---

## 🛒 Booking Domain

### `business.booking.attempt`
**Type:** Counter  
**Description:** Outcome count for booking creation attempts.

| Tag Key | Tag Values | Description |
| :--- | :--- | :--- |
| `status` | `success`, `failure` | Overall operation outcome. |
| `reason` | `none` (success) | Baseline for successful bookings. |
|  | `SEAT_ALREADY_BOOKED`, `SHOWTIME_NOT_FOUND`, `INTERNAL_ERROR`, ... | Error code for failure paths. |
| `theater_id` | `<theater-uuid>`, `--global` | Theater scope dimension. |

### `business.booking.attempt.latency`
**Type:** Timer  
**Description:** Latency for booking creation flow.

| Tag Key | Tag Values | Description |
| :--- | :--- | :--- |
| `status` | `success`, `failure` | Latency by outcome. |
| `theater_id` | `<theater-uuid>`, `--global` | Latency by theater scope. |

---

## ⚙️ Async Reliability Domain

### `async.execution.failure`
**Type:** Counter  
**Source:** `AsyncExceptionHandler`  
**Description:** Counts uncaught exceptions from `@Async` methods.

| Tag Key | Tag Values | Description |
| :--- | :--- | :--- |
| `method` | Java method name | Async method that failed. |
| `exception` | Exception simple class name | Failure type (`RuntimeException`, etc.). |

---

## 🔍 Actuator Exposure Notes

TicketLedger exposes Health/Metrics/Prometheus via Spring Actuator, but endpoint access is controlled by security policy:
- `/actuator/health` is public for liveness.
- `/actuator/**` is restricted to `OWNER`.
