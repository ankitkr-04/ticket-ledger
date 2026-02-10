# 010 - Booking Failure Recovery

## Purpose

Phase 2 requires that TicketLedger remains **financially correct** and **seat-consistent** across system/network failures in the highest-risk window:

> **Window:** `Booking.status = HELD` (created) → `Booking.status = CONFIRMED` (payment finalized)

This document defines the **Booking Reliability Matrix** (failure → detection → policy), the **Reclamation (Bump) invariant**, and the **notification strategy**.

## Core Invariants (Non-Negotiable)

1. **Database is the Source of Truth:** Booking success is defined by the database ledger (`bookings` + `payments`), not by email/webhook delivery.
2. **Original Payer Wins:** If a payment is verifiably successful for an earlier booking (User 1), that user is entitled to the seat even if it was later re-sold (User 2).
3. **Seat Moves Are Transactional:** Any seat reassignment (including reclamation) happens inside a single DB transaction with explicit locking.
4. **Side Effects After Commit:** Emails/notifications are `AFTER_COMMIT` side effects; failures there never roll back ledger state.

## Timers & Definitions

- **Hold expiry:** `bookings.locked_until` (default remains `created_at + 10 minutes` per `003_sequence_flows.md`).
- **Abandonment cutoff (fast-path):** `2 minutes` from booking creation **when no payment has been initiated** (no `payments` row, or latest payment has no `provider_transaction_id`).
- **Safety buffer (clock drift / gateway lag):** `+30 seconds` applied before taking irreversible cleanup actions.
  - Effective cleanup threshold is `deadline + 30s`, where `deadline` is either the 2-minute abandonment cutoff or `locked_until`.

---

## Section A: Booking Reliability Matrix

| Scenario                                                               | Detection Mechanism                                                                                                                                                                                                                                       | Resolution Policy                                                                                                                                                                                                                                      |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **User Abandons at Stripe**                                            | `BookingCleanupScheduler` finds `bookings.status = HELD` and `created_at < now() - 2m - 30s` and **no initiated payment** (`payments` missing or latest has `provider_transaction_id IS NULL`).                                                           | **Expire + Release:** transition booking → `EXPIRED`, set related seats → `AVAILABLE`. **Do not hard-delete** ledger rows during Phase 2.                                                                                                              |
| **Late Payment (No Conflict)**                                        | Reconciliation detects gateway payment `SUCCEEDED` for User 1 after booking expiry, and the seat is currently `AVAILABLE` (no other confirmed booking owns it).                                                                                         | **Reclaim Seat:** assign seat back to User 1 booking and mark User 1 → `CONFIRMED`.                                                                                                                               |
| **Seat Conflict (The Bump)**                                          | Reconciliation detects gateway payment `SUCCEEDED` for User 1 after expiry, but the seat is now owned by User 2 (User 2 has a `CONFIRMED` booking for the same seat/showtime).                                                                         | **Original Payer Wins:** User 2 → `SYSTEM_CANCELLED` + refund; User 1 → `CONFIRMED`. Record `AdminAuditLog` action `AUTO_RECLAMATION_CONFLICT`.                                                           |
| **Refund API Failure (Bump Debt)**                                    | During Seat Conflict processing, the call to `Stripe.refund(User2)` fails (timeout/5xx or non-retryable 4xx) after User 2 has been bumped.                                                                                                            | Persist **financial debt:** User 2 → `REFUND_REQUIRED_MANUAL` + admin alert/audit (`AUTO_RECLAMATION_REFUND_FAILED`). User 1 remains `CONFIRMED`.                                                        |
| **DB Deadlock on Create**                                              | Spring `DataAccessException` / deadlock SQLSTATE caught in `BookingController` during the seat-hold + booking-create transaction.                                                                                                                         | **Retry internally 3x** (small jitter). If still failing, return **`503 Service Unavailable`**. The client retries with the same idempotency key; the idempotency record remains `PENDING` until a successful commit occurs.                           |
| **API Crash After Seats Held, Before Response**                        | Server dies after DB commit but before returning `201 Created` to client.                                                                                                                                                                                 | **Idempotency Healing:** client retry with same idempotency key returns the already-created booking + payment initiation details; no double-hold occurs.                                                                                               |
| **API Crash Mid-Transaction**                                          | Process dies before commit while attempting to hold seats and create booking/payment records.                                                                                                                                                             | **Automatic rollback:** DB transaction aborts; seats remain `AVAILABLE` and no booking exists. Client retries normally.                                                                                                                                |
| **Payment Gateway Timeout / Network Error (Initiate Payment Session)** | Payment session creation call fails (timeout/5xx) after booking/payment row(s) exist.                                                                                                                                                                     | Booking remains `HELD`. Payment row remains `PENDING` without `provider_transaction_id`. Client can retry `POST /bookings/{id}/payment-intents` (Flow A.1). Cleanup scheduler applies abandonment cutoff (`2m + 30s`) if no initiation ever succeeded. |
| **Webhook Lost / Never Delivered**                                     | `BookingCleanupScheduler` finds `payments.status = PENDING` older than reconciliation threshold and `provider_transaction_id IS NOT NULL`.                                                                                                                | **Synthetic Webhook:** query Stripe by `provider_transaction_id` and apply the same state transitions as the webhook handler (`SUCCESS` → confirm, `FAILED` → keep booking `HELD` for retry, `SUCCEEDED after expiry` → reclamation/refund-required).  |
| **Webhook Duplicate / Out of Order**                                   | Webhook handler receives the same event multiple times or receives `FAILED` after `SUCCEEDED`.                                                                                                                                                            | **Idempotent processing:** always load `payments` + `bookings` `FOR UPDATE`, and enforce monotonic transitions: `SUCCESS` is terminal for that payment attempt; ignore regressions.                                                                    |
| **Email Service Down**                                                 | Async notification execution fails (exception in event listener / email client).                                                                                                                                                                          | **No rollback:** booking remains `CONFIRMED`. User experience relies on “My Bookings” as the canonical view. Retrying email is best-effort only.                                                                                                       |
| **After-Commit Event Dropped**                                         | `@TransactionalEventListener(AFTER_COMMIT)` is never executed due to JVM crash immediately after commit.                                                                                                                                                  | **Acceptable at-most-once:** ledger is correct; notifications may be missing. Optional periodic notifier can re-send based on ledger, but is not required for booking correctness.                                                                     |
| **Cleanup Scheduler Missed Runs**                                      | Observability detects job gaps (no heartbeat), and `HELD` bookings accumulate past `locked_until`.                                                                                                                                                        | On scheduler recovery, process backlog in order of earliest `locked_until`. Policy remains deterministic (expire, reconcile, reclaim) so late execution does not create inconsistency.                                                                 |

---

## Section B: The Reclamation Invariant (User 1 vs User 2)

### When Reclamation Applies

Reclamation is attempted when **User 1 has a verified successful payment** for a booking that is no longer holding the seat (booking expired / seat released), and the seat may have been acquired by another user (User 2).

### Reclamation Logic (Step-by-Step)

1. **Verify User 1 payment in Stripe**
   - Use `payments.provider_transaction_id` and Stripe status to verify `SUCCEEDED`.
2. **Acquire locks (single transaction)**
   - Lock the **Seat row** with `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) to prevent a third user from taking the seat during the swap.
   - Lock both relevant **Booking rows** (`User1Booking`, and if present, `User2Booking`) `FOR UPDATE`.
3. **Check seat occupancy**
   - If seat is `AVAILABLE` → assign seat to User 1 booking and mark `CONFIRMED`.
   - If seat is occupied by **User 2** (User 2 has a `CONFIRMED` booking for the same seat/showtime):
     1. Mark User 2 booking as **system-cancelled** (`SYSTEM_CANCELLED`) and set seat to be reassigned.
        - Phase 2 requirement: `SYSTEM_CANCELLED` is a first-class booking state (no `CANCELLED` fallback).
     2. Call `Stripe.refund(User2)` (gateway refund).
     3. Assign seat to User 1 booking and mark `CONFIRMED`.
     4. Write `AdminAuditLog` with action `AUTO_RECLAMATION_CONFLICT`.
4. **Refund failure handling**
   - If `Stripe.refund(User2)` fails (timeout/4xx/5xx), persist a durable state indicating **financial debt**:
     - Mark User 2 booking as `REFUND_REQUIRED_MANUAL` (admin intervention required), and create an audit log entry with action `AUTO_RECLAMATION_REFUND_FAILED`.
     - User 1 still wins the seat (seat is reassigned to User 1).

### Reclamation Flow

```mermaid
flowchart TD
  A[Scheduler/Reconciler finds late payment success for User 1] --> B[Verify Stripe SUCCEEDED]
  B --> C[TX: lock Seat PESSIMISTIC_WRITE]
  C --> D{Seat available?}
  D -->|Yes| E[Assign Seat to User 1; Booking1 -> CONFIRMED]
  D -->|No, occupied by User 2| F[Lock Booking2 FOR UPDATE]
  F --> G[Mark Booking2 SYSTEM_CANCELLED]
  G --> H[Call Stripe.refund(User2)]
  H --> I{Refund ok?}
  I -->|Yes| J[Assign Seat to User 1; Booking1 -> CONFIRMED]
  I -->|No| K[Booking2 -> REFUND_REQUIRED_MANUAL; audit debt]
  J --> L[AdminAuditLog: AUTO_RECLAMATION_CONFLICT]
  K --> M[AdminAuditLog: AUTO_RECLAMATION_REFUND_FAILED]
```

---

## Section C: Notification Strategy

**Notifications are non-transactional side effects. Success of a booking is defined by the Database Ledger, not the delivery of an email.**

Operationally:
- Notification handlers run `AFTER_COMMIT`.
- Email failures are logged and may be retried best-effort.
- The user-facing canonical recovery surface is **“My Bookings”**, powered by the ledger.

---

## Cross-References

- State machine contract: `docs/architecture/002_lifecycle_states.md`
- Booking/payment flows + scheduler: `docs/architecture/003_sequence_flows.md`
- Storage contract (`bookings.locked_until`, `payments`): `docs/architecture/004_database_schema.md`
- Admin recovery outcomes (refund failures): `docs/architecture/009_admin_failure_modes.md`
