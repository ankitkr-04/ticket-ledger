# Decision 007: Reclamation & Lock Strategy (Late Payments)

## 📋 Purpose

This document records the **trade-offs and implementation strategy** for handling late gateway confirmations in the critical window:

> Booking created (`HELD`) → payment finalized (`CONFIRMED`)

Specifically, it codifies:
- **Original payer priority** (who “wins” the seat)
- **Locking strategy** during release → reclaim (the “bump”)
- **Clock-drift buffer** to avoid premature release
- **Refund failure handling** (financial debt)

### ✅ This file contains:
- Decision + rationale
- Locking and ordering rules
- Failure handling contract for automated bumps

### ❌ This file does NOT contain:
- Booking status enum changes (implementation gate)
- Full API contracts
- Stripe integration details beyond required semantics

---

## 1. Context

TicketLedger uses:
- `bookings.status` as the **financial ledger**
- `seats.status` as a **materialized lock state**

However, real systems have failure modes where:
- the seat hold expires / is released
- the gateway later reports the payment as `SUCCEEDED`
- the seat may have been acquired by another user

This is the **highest-risk correctness window** for Phase 2 stability.

---

## 2. Problem

We must preserve three properties under network/system failures:

1. **Financial correctness:** a verified successful payment must not be silently dropped.
2. **Seat integrity:** only one user may own the seat at the end of recovery.
3. **Determinism:** recovery must converge to a single outcome, even if jobs re-run.

The hard case is the **Seat Conflict (The Bump)**:
- User 1’s payment is verified as `SUCCEEDED` after expiry
- User 2 already holds a `CONFIRMED` booking for the same seat/showtime

---

## 3. Decision

### 3.1 Original Payer Priority (User 1 wins)

**Decision:** Favor the **first user with a verifiably successful payment** (User 1), even if the webhook was late and the seat was re-sold to User 2.

**Why:**
- The system cannot treat gateway success as optional; it is a financial commitment.
- A deterministic rule avoids endless dispute states and reduces admin workload.
- Consistent with “payments are the source of truth” for entitlement.

**Cost / UX downside:**
- User 2 can be bumped after seeing a confirmation.

**Mitigations:**
- Record an immutable audit entry (`AUTO_RECLAMATION_CONFLICT`).
- Immediate automated refund attempt for User 2.
- Clear user messaging (User 2 sees system cancellation + refund status in “My Bookings”).

### 3.2 Locking Strategy (PESSIMISTIC_WRITE on Seat)

**Decision:** During reclamation, acquire a **`PESSIMISTIC_WRITE` lock on the Seat row** to prevent a third user from taking the seat during the swap.

**Scope:** One DB transaction that:
1. Locks `Seat` (`FOR UPDATE`)
2. Locks `User1Booking` (`FOR UPDATE`)
3. If conflict, locks `User2Booking` (`FOR UPDATE`)
4. Performs seat reassignment and booking state transitions

**Why:**
- The “release → reclaim” window is a race; optimistic retries alone can oscillate.
- Seat is the shared resource; locking it creates a single-writer path.

**Trade-off:**
- Reduced concurrency for that specific seat during recovery.
- Requires careful lock ordering to avoid deadlocks.

**Lock ordering rule:**
- Always lock `Seat` first, then `Booking` rows in a stable order (e.g., by UUID).

### 3.3 30-Second Buffer (Clock Drift / Gateway Lag)

**Decision:** Add a **30-second safety buffer** before taking irreversible cleanup actions (expire/release or reclaim decisions).

**Why:**
- Prevents premature release when a user is mid-redirect or gateway capture is slightly delayed.
- Protects against minor clock drift between TicketLedger and the gateway timestamps.

**Trade-off:**
- Seats remain unavailable slightly longer under abandonment.

### 3.4 Refund Failure Handling (Financial Debt)

**Decision:** If the automated refund for a bumped User 2 fails, persist a durable state for admin follow-up.

**Contract:**
- User 1 remains entitled and ends `CONFIRMED`.
- User 2 is marked `REFUND_REQUIRED_MANUAL`.
- Emit an admin alert signal and persist an audit record (`AUTO_RECLAMATION_REFUND_FAILED`).

**Why:**
- Refund API failure is not a consistency failure; it is a settlement failure.
- The ledger must retain a clear “owed money” state for operations.

---

## 4. Consequences

- Recovery becomes deterministic and convergent:
  - Re-running reconciliation yields the same “User 1 wins” outcome.
- Correctness is anchored on:
  - gateway-verified `SUCCEEDED`
  - seat-level serialization during reclaim
- Admin workload is minimized but not eliminated:
  - rare manual refund debt cases remain explicitly visible.

---

## 5. References

- Reliability matrix + reclamation flow: `docs/architecture/010_booking_failure_recovery.md`
- State machines: `docs/architecture/002_lifecycle_states.md`
- Concurrency and scheduler flows: `docs/architecture/003_sequence_flows.md`
- Storage contracts (`locked_until`, payments): `docs/architecture/004_database_schema.md`
