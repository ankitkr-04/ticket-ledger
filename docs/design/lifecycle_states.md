```markdown
# TicketLedger: Lifecycle States & Transition Rules

## 1. Purpose

This document defines the **allowed and denied state transitions** for all entities in TicketLedger. It serves as the **finite state machine contract** that governs system behavior.

This file contains:
* State definitions
* Allowed transitions
* Denied/illegal transitions
* Inter-entity coupling rules

This file does NOT contain:
* API flows
* Timing logic
* Retry mechanisms
* Implementation details

---

## 2. Entity States

### 2.1 Seat States

* `AVAILABLE` — Seat is free and can be reserved.
* `HELD` — Seat is temporarily locked for a user during checkout.
* `SOLD` — Seat is permanently booked and paid for.

### 2.2 Booking States

* `HELD` — Booking created, seats reserved, awaiting payment.
* `CONFIRMED` — Payment successful, booking finalized.
* `EXPIRED` — Hold duration exceeded, booking invalidated.
* `CANCELLED` — User-initiated cancellation after confirmation.
* `COMPLETED` — Showtime passed, booking lifecycle ended.
* `REFUND_REQUIRED` — Payment succeeded but booking integrity violated (e.g., expired hold was re-allocated).

### 2.3 Payment States

* `PENDING` — Payment initiated, awaiting gateway response.
* `SUCCESS` — Payment confirmed by gateway.
* `FAILED` — Payment rejected by gateway.
* `REFUNDED` — Payment reversed due to cancellation or conflict.

### 2.4 Showtime States

* `ACTIVE` — Showtime is open for bookings.
* `PAUSED` — Showtime temporarily suspended (admin action).
* `INACTIVE` — Showtime closed permanently (past showtime or cancelled).

---

## 3. Seat — Transition Matrix

### Allowed Transitions

* `AVAILABLE → HELD`
* `HELD → AVAILABLE`
* `HELD → SOLD`
* `SOLD → AVAILABLE` (only via `Booking: CONFIRMED → CANCELLED`)


### Denied Transitions

* `AVAILABLE → SOLD`
* `SOLD → HELD`
* `SOLD → EXPIRED`
* `AVAILABLE → EXPIRED`

### Rule

Seat never transitions directly based on payment or time. Seat reacts only to Booking decisions inside a transaction.

---

## 4. Booking — Transition Matrix

### Allowed Transitions

* `HELD → CONFIRMED`
* `HELD → EXPIRED`
* `CONFIRMED → CANCELLED`
* `CONFIRMED → COMPLETED`
* `EXPIRED → REFUND_REQUIRED` (late payment after hold expiry)
* `CONFIRMED → REFUND_REQUIRED`

### Denied Transitions

* `EXPIRED → CONFIRMED`
* `CANCELLED → CONFIRMED`
* `COMPLETED → ANY`
* `REFUND_REQUIRED → CONFIRMED`

### Rule

Booking state is append-only in intent. You never "revive" a dead booking.

---

## 5. Payment — Transition Matrix

### Allowed Transitions

* `PENDING → SUCCESS`
* `PENDING → FAILED`
* `SUCCESS → REFUNDED`

### Denied Transitions

* `FAILED → SUCCESS`
* `REFUNDED → SUCCESS`
* `SUCCESS → PENDING`

### Rule

Payment is an external truth recorder, not a controller.

---

## 6. Showtime — Transition Matrix

### Allowed Transitions

* `ACTIVE → PAUSED`
* `PAUSED → ACTIVE`
* `ACTIVE → INACTIVE`
* `PAUSED → INACTIVE`

### Denied Transitions

* `INACTIVE → ACTIVE`
* `INACTIVE → PAUSED`

### Rule

Showtime is monotonic toward INACTIVE.

---

## 7. Inter-Entity Transition Constraints

* `Seat: AVAILABLE → HELD` requires `Showtime == ACTIVE`
* `Booking: HELD → CONFIRMED` requires `Payment == SUCCESS`
* `Booking: HELD → EXPIRED` forces `Seat: HELD → AVAILABLE`
* `Booking: CONFIRMED → CANCELLED` forces `Seat: SOLD → AVAILABLE`
* `Showtime: INACTIVE` denies any new `Seat: AVAILABLE → HELD`

---

