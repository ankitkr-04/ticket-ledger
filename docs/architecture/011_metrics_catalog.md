# 011: Metrics Catalog

## 📋 Purpose
Defines the standard business metrics emitted by the TicketLedger core.
Used by DevOps for dashboard creation and SRE for alerting.

## 📊 Naming Convention
- **Format:** `business.<domain>.<action>`
- **Style:** Lowercase, dot-separated.
- **Tags:** Low-cardinality dimensions only (No UserIDs, No BookingIDs).

## 🛒 Booking Domain

### `business.booking.attempt`
**Type:** Counter
**Description:** Tracks the outcome of every booking creation request.

| Tag Key | Tag Values | Description |
| :--- | :--- | :--- |
| `status` | `success` | Booking successfully created (or idempotency hit). |
| | `failure` | Booking failed due to business rule or system error. |
| `reason` | `none` | Present when status is success. |
| | `SEAT_ALREADY_BOOKED` | Concurrency conflict or unavailable seat. |
| | `SHOWTIME_NOT_FOUND` | Invalid showtime ID. |
| | `PAYMENT_GATEWAY_ERROR` | Upstream failure from Stripe. |
| | `INTERNAL_ERROR` | Unexpected RuntimeException (NPE, etc). |

**Alerting Rules:**
- **High Failure Rate:** `rate(failure) / rate(total) > 5%` (Severity: Warning)
- **Payment Outage:** `rate(failure, reason="PAYMENT_GATEWAY_ERROR") > 0` (Severity: Critical)

---

### `business.booking.attempt.latency`
**Type:** Timer
**Description:** End-to-end execution time of the booking flow (including DB lock & Payment setup).

| Tag Key | Tag Values | Description |
| :--- | :--- | :--- |
| `status` | `success`, `failure` | Segment latency by outcome. |

**SLO Target:**
- P95 < 500ms
- P99 < 2000ms
