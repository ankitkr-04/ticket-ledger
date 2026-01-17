# TicketLedger: Problem Statement & System Invariants

## 1. Context

TicketLedger is a backend engine for movie theater bookings (e.g., Avatar at PVR, Bhopal). The system must manage high-demand inventory (seats) where the ratio of users to available resources is high during peak bursts.

## 2. Core Problem

The primary challenge is maintaining **transactional integrity** under high concurrency without using external distributed locks (like Redis). We must prevent **double-booking** (Lost Updates) and manage **stale inventory** (Expired Holds) while ensuring the system remains the **Single Source of Truth**.

## 3. Business Rules (Phase 1)

* **Global Configuration:** For the current phase, constraints are managed via a global settings table.
* **Tiered Booking Limits:**
  * **Verified Users:** Max 5 seats per transaction.
  * **Unverified Users:** Max 2 seats per transaction.
* **Refund Policy:** Refunds are permitted only if the cancellation request timestamp is at least 3 hours before showtime.
* **Hold Duration:** Seats are held for a configurable duration (e.g., 5-10 minutes) before being released back to the pool.
* **Expired Holds:** Expired holds may be released lazily; temporary staleness is acceptable within bounded limits.

## 4. Technical Invariants (The "Rules of the Universe")

* **Exclusive Resource Access:** The system must use **Pessimistic Locking** at the database level during the `AVAILABLE` → `HELD` transition.
* **Atomic State Transitions:** A seat must never exist in an intermediate state. Transitions must be atomic:
  * `AVAILABLE` → `HELD`
  * `HELD` → `BOOKED`
  * `HELD` → `AVAILABLE` (on expiry or failure)
* **Single Source of Truth:** The database is the only authority for seat state; application memory is treated as ephemeral and non-authoritative.
* **Idempotency:** Payment confirmation webhooks must be idempotent. Processing the same "Success" signal twice must not result in duplicate seat allocations or corrupted ledger entries.
* **Conflict Resolution:** If a payment arrives for an expired hold that has been re-allocated, the system must move the transaction to a `REFUND_REQUIRED` state to protect the ledger's integrity.

## 5. Non-Goals (Phase 1)

* No external caching (Redis/Memcached).
* No asynchronous message brokers (Kafka/RabbitMQ).
* No complex UI/Frontend (Focus is API and Logic).

## 6. Failure Ownership Boundaries

* The database guarantees atomicity and isolation for seat state transitions.
* The application layer is responsible for enforcing business rules and idempotency.
* External systems (payment gateway) are treated as unreliable and retry-prone.
* Network failures and duplicate callbacks are expected and handled defensively.

## 7. Consistency Guarantees

* Strong consistency is guaranteed for seat state within a single show.
* Read-after-write consistency is guaranteed for booking confirmation APIs.
* Temporary staleness in availability reads is acceptable within bounded time.
* Cross-show or analytics queries may be eventually consistent.