# 009 - Admin Failure Modes

## Contract of Recovery

| Scenario | Gateway Signal | Scheduler Action | Final Booking State |
|---|---|---|---|
| Gateway Timeout (5xx/Network) | Retryable error (`BusinessException`) | Retry reconciliation job | `REFUNDED` |
| Gateway Logic Error (4xx) | `PermanentGatewayException` | Stop retries, mark terminal failure | `REFUND_FAILED` |
| Stale "Already Refunded" (400) | Stripe code `charge_already_refunded` | Heal by treating response as success | `REFUNDED` |
| Application Crash | Stuck `INITIATED` audit log on restart | Re-run scheduler reconciliation | `REFUNDED` |

## State Rules

- `admin_audit_log.status = PERMANENT_FAILURE` means terminal and non-retryable.
- Scheduler work selection must only include `INITIATED` logs.
- `bookings.status = REFUND_FAILED` indicates manual intervention required; seat remains sold.
