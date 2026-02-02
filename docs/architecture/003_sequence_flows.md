# TicketLedger: Sequence Flows

## 📋 Purpose

This document defines the **end-to-end transaction flows** for TicketLedger. Each flow represents a complete business operation from trigger to outcome, including both happy paths and failure scenarios.

### ✅ This file contains:
- API-triggered flows
- Asynchronous event flows
- Background job flows
- Transaction boundaries
- Error handling paths

### ❌ This file does NOT contain:
- State definitions (see `02_lifecycle_states.md`)
- Database schema details
- Implementation code
- Retry mechanisms

---

## 🎯 Flow Categories

| Category             | Flows        | Trigger Type              |
| -------------------- | ------------ | ------------------------- |
| **Core Transaction** | A, A.1, B, C | User API + Webhook + Cron |
| **Background Jobs**  | D, F         | Cron                      |
| **Refund & Cleanup** | E            | User API                  |
| **Error Handling**   | G            | System Exception          |
| **Concurrency**      | H            | Concurrent Requests       |

---

## 🔄 Core Transaction Flows

### Flow A: Reserve Seats (The Initiation)

**Trigger:** `POST /bookings`

**Input:**
```json
{
  "userId": "user123",
  "showtimeId": "show456",
  "seatIds": ["A1", "A2", "A3"]
}
```

**Purpose:** Lock seats, create booking with HELD status, initiate FIRST payment attempt

**Sequence:**

```mermaid
sequenceDiagram
    participant User
    participant API
    participant DB
    participant PaymentGateway

    User->>API: POST /bookings
    API->>API: Validate User & Limits
    API->>API: Sort seatIds (Deadlock Prevention)
    API->>DB: BEGIN TRANSACTION
    API->>DB: SELECT ... FOR UPDATE (Lock Seats)
    
    alt Seats Available
        API->>DB: Verify Showtime == ACTIVE
        API->>DB: UPDATE Seats → HELD
        API->>DB: INSERT Booking (HELD)
        API->>DB: INSERT Payment (PENDING, attempt=1)
        API->>DB: COMMIT
        API->>PaymentGateway: Initiate Payment Session
        API-->>User: 201 Created (bookingId, paymentId, paymentUrl)
    else Seats Unavailable
        API->>DB: ROLLBACK
        API-->>User: 409 Conflict
    end
```

**Transaction Boundary:**
- **START:** `BEGIN TRANSACTION`
- **LOCKS:** `SELECT ... FOR UPDATE` on `seats` table (sorted by `seat_id`)
- **WRITES:**
  1. `UPDATE seats SET status = HELD` (Lock Acquisition - FIRST)
  2. `INSERT INTO bookings` (`status = HELD`, `locked_until = now() + 10 min`)
  3. `INSERT INTO payments` (`status = PENDING`, `attempt_number = 1`)
- **END:** `COMMIT` or `ROLLBACK`

**Validations:**
1. User exists and is active
2. Showtime exists and `status == ACTIVE`
3. All `seatIds` exist and `status == AVAILABLE`
4. User has not exceeded booking limits

**Outcome:**
- ✅ **Success:** Returns `bookingId`, `paymentId`, and `paymentUrl`
- ❌ **Failure:** `409 Conflict` (seats taken) or `400 Bad Request` (validation failure)

**Note:** If payment fails, user can retry via Flow A.1 without losing seat hold (if within 10 min window).

---

### Flow A.1: Retry Payment (The Retry)

**Trigger:** `POST /bookings/{id}/payment-intents`

**Input:**
```json
{
  "bookingId": "booking-uuid-123"
}
```

**Purpose:** Initiate NEW payment attempt for existing HELD booking after initial payment failure

**Business Context:**
- User reserved seats (Flow A)
- First payment attempt failed (card declined)
- Booking still HELD (within 10-minute window)
- User wants to try different payment method

**Sequence:**

```mermaid
sequenceDiagram
    participant User
    participant API
    participant DB
    participant PaymentGateway

    User->>API: POST /bookings/{id}/payment-intents
    API->>DB: BEGIN TRANSACTION
    API->>DB: SELECT booking, payments FOR UPDATE
    
    alt Booking == HELD && within timeout
        alt Payment attempts < 3
            API->>DB: UPDATE previous payment → FAILED (if PENDING)
            API->>DB: INSERT new Payment (PENDING, attempt=N+1)
            API->>DB: COMMIT
            API->>PaymentGateway: Initiate New Payment Session
            API-->>User: 201 Created (new paymentId, paymentUrl)
        else Max attempts reached
            API->>DB: ROLLBACK
            API-->>User: 400 MAX_PAYMENT_ATTEMPTS
        end
    else Booking EXPIRED or not HELD
        API->>DB: ROLLBACK
        API-->>User: 400 BOOKING_EXPIRED
    end
```

**Transaction Boundary:**
- **START:** `BEGIN TRANSACTION`
- **LOCKS:** `SELECT ... FOR UPDATE` on `bookings` and `payments`
- **WRITES:**
  1. `UPDATE payments SET status = FAILED WHERE booking_id = ? AND status = PENDING`
  2. `INSERT INTO payments` (`status = PENDING`, `attempt_number = attempt_number + 1`)
- **END:** `COMMIT` or `ROLLBACK`

**Business Rules:**
- Maximum 3 payment attempts per booking
- Does NOT extend `locked_until` timestamp (original 10-min window remains)
- Only allowed when booking is `HELD`
- Seats remain locked during retry

**Key Difference from Flow A:**
- Flow A: Creates booking + first payment
- Flow A.1: Creates additional payment for existing booking
- Database supports 1:N relationship (bookings → payments)

**Outcome:**
- ✅ **Success:** New payment initiated, user redirected to payment gateway
- ❌ **Expired:** `400 BOOKING_EXPIRED` - seats already released
- ❌ **Max Attempts:** `400 MAX_PAYMENT_ATTEMPTS` - user must create new booking

---

### Flow B: Payment Webhook (The Resolution)

**Trigger:** Asynchronous Webhook from Payment Gateway

**Input:**
```json
{
  "paymentId": "pay789",
  "status": "SUCCESS" | "FAILED",
  "transactionId": "txn123",
  "timestamp": "2026-01-18T10:30:00Z"
}
```

**Sequence:**

```mermaid
sequenceDiagram
    participant Gateway
    participant Webhook
    participant DB
    participant RefundService

    Gateway->>Webhook: POST /webhooks/payment
    Webhook->>DB: BEGIN TRANSACTION
    Webhook->>DB: SELECT payment, booking FOR UPDATE
    
    alt Payment SUCCESS
        alt Booking == HELD (On Time)
            Webhook->>DB: UPDATE payment → SUCCESS
            Webhook->>DB: UPDATE booking → CONFIRMED
            Webhook->>DB: UPDATE seats → SOLD
            Webhook->>DB: COMMIT
            Webhook-->>Gateway: 200 OK
        else Booking == EXPIRED (Late Payment)
            Webhook->>DB: SELECT seats status
            alt Seats == AVAILABLE
                Webhook->>DB: UPDATE payment → SUCCESS
                Webhook->>DB: UPDATE booking → CONFIRMED
                Webhook->>DB: UPDATE seats → SOLD
                Webhook->>DB: COMMIT
                Webhook-->>Gateway: 200 OK (Reclaimed)
            else Seats == SOLD
                Webhook->>DB: UPDATE payment → SUCCESS
                Webhook->>DB: UPDATE booking → REFUND_REQUIRED
                Webhook->>DB: COMMIT
                Webhook->>RefundService: Initiate Refund
                Webhook-->>Gateway: 200 OK (Refund Queued)
            end
        end
    else Payment FAILED
        Webhook->>DB: UPDATE payment → FAILED
        Webhook->>DB: COMMIT (Keep booking HELD for retry)
        Webhook-->>Gateway: 200 OK
    end
```

**Transaction Boundary:**
- **START:** `BEGIN TRANSACTION`
- **LOCKS:** `SELECT ... FOR UPDATE` on `payments` and `bookings`
- **WRITES:**
  - `UPDATE payments SET status = SUCCESS/FAILED`
  - `UPDATE bookings SET status = CONFIRMED/REFUND_REQUIRED`
  - `UPDATE seats SET status = SOLD` (if applicable)
- **END:** `COMMIT`

**Decision Tree:**

| Payment Status | Booking Status | Seat Status | Action                                   |
| -------------- | -------------- | ----------- | ---------------------------------------- |
| `SUCCESS`      | `HELD`         | `HELD`      | Confirm booking → Seats `SOLD`           |
| `SUCCESS`      | `EXPIRED`      | `AVAILABLE` | Reclaim booking → Confirm + Seats `SOLD` |
| `SUCCESS`      | `EXPIRED`      | `SOLD`      | Mark `REFUND_REQUIRED` → Initiate refund |
| `FAILED`       | `HELD`         | `HELD`      | Update payment only (allow retry)        |
| `FAILED`       | `EXPIRED`      | N/A         | No action (already cleaned up)           |

**Idempotency:**
- Webhook endpoint must handle duplicate calls
- Check `payment.status` before processing
- Return `200 OK` if already processed

**Outcome:**
- ✅ **Success:** Booking confirmed or refund initiated
- ⚠️ **Late Success:** Seats reclaimed if available, else refunded
- ❌ **Failure:** Payment marked failed, booking remains `HELD`

---

### Flow C: User Redirect (The Sync)

**Trigger:** User lands on `/booking-status?bookingId={id}`

**Purpose:** Force synchronization with payment gateway when webhook may be delayed

**Sequence:**

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant API
    participant DB
    participant PaymentGateway

    User->>Frontend: Redirect from Payment Page
    Frontend->>API: GET /bookings/{id}/status
    API->>DB: SELECT booking, payment
    
    alt Payment == PENDING (Ambiguous State)
        API->>PaymentGateway: GET /payments/{paymentId}/status
        PaymentGateway-->>API: {status: SUCCESS/FAILED}
        API->>DB: BEGIN TRANSACTION
        API->>API: Apply Flow B Logic
        API->>DB: COMMIT
        API-->>Frontend: Updated Status
    else Payment == SUCCESS/FAILED (Definitive)
        API-->>Frontend: Current Status
    end
    
    Frontend-->>User: Show Booking Confirmation/Failure
```

**Polling Strategy:**
- Frontend polls every 2 seconds for up to 30 seconds
- After 30 seconds, show "Payment Processing" message
- Backend forces gateway fetch only once per booking

**Transaction Boundary:**
- Same as Flow B if update required
- Read-only if status is already definitive

**Outcome:**
- ✅ **Sync Success:** User sees updated status immediately
- ⏳ **Pending:** User sees "Payment Processing" message
- ❌ **Timeout:** User advised to check email/contact support

---

### Flow D: The Reaper (Background Cleanup)

**Trigger:** Cron Job (Every 1 minute)

**Purpose:** Clean up expired holds and free seats

**Sequence:**

```mermaid
sequenceDiagram
    participant Cron
    participant DB
    
    Cron->>DB: SELECT bookings WHERE status=HELD AND locked_until < now()
    
    loop For Each Expired Booking
        Cron->>DB: BEGIN TRANSACTION
        Cron->>DB: SELECT booking, seats FOR UPDATE
        Cron->>DB: UPDATE booking → EXPIRED
        Cron->>DB: UPDATE seats → AVAILABLE
        Cron->>DB: UPDATE payment → FAILED (if still PENDING)
        Cron->>DB: COMMIT
    end
    
    Cron->>Cron: Log cleanup metrics
```

**Transaction Boundary:**
- **Batch Size:** 100 bookings per run
- **Per Booking:**
  - `BEGIN TRANSACTION`
  - `SELECT ... FOR UPDATE` on booking and related seats
  - `UPDATE bookings SET status = EXPIRED`
  - `UPDATE seats SET status = AVAILABLE`
  - `UPDATE payments SET status = FAILED` (if `PENDING`)
  - `COMMIT`

**Query:**
```sql
SELECT id, seat_ids 
FROM bookings 
WHERE status = 'HELD' 
  AND locked_until < NOW()
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

**Outcome:**
- ✅ **Seats Released:** Available for new bookings
- 📊 **Metrics Logged:** Number of expired bookings cleaned
- ⚠️ **Race Condition:** `SKIP LOCKED` prevents conflicts with concurrent webhooks

---

## 💰 Refund & Cleanup Flows

### Flow E: User Cancellation (The Refund)

**Trigger:** `POST /bookings/{id}/cancel`

**Business Rule:** Cancellation allowed only if `showtime_start - current_time >= 3 hours`

**Sequence:**

```mermaid
sequenceDiagram
    participant User
    participant API
    participant DB
    participant PaymentGateway

    User->>API: POST /bookings/{id}/cancel
    API->>DB: SELECT booking, showtime
    
    alt Time Check PASS (>= 3 hours)
        alt Booking == CONFIRMED
            API->>PaymentGateway: POST /refunds (paymentId)
            PaymentGateway-->>API: Refund Initiated
            API->>DB: BEGIN TRANSACTION
            API->>DB: UPDATE booking → CANCELLED
            API->>DB: UPDATE payment → REFUNDED
            API->>DB: UPDATE seats → AVAILABLE
            API->>DB: COMMIT
            API-->>User: 200 OK (Refund Processing)
        else Booking != CONFIRMED
            API-->>User: 400 Bad Request (Cannot cancel)
        end
    else Time Check FAIL (< 3 hours)
        API-->>User: 400 Bad Request (Too late to cancel)
    end
```

**Transaction Boundary:**
- **START:** After successful refund initiation
- **LOCKS:** `SELECT ... FOR UPDATE` on booking and seats
- **WRITES:**
  - `UPDATE bookings SET status = CANCELLED, cancelled_at = now()`
  - `UPDATE payments SET status = REFUNDED`
  - `UPDATE seats SET status = AVAILABLE`
- **END:** `COMMIT`

**Validations:**
1. Booking exists and belongs to user
2. Booking status is `CONFIRMED`
3. Showtime start time is at least 3 hours away
4. Payment gateway accepts refund request

**Outcome:**
- ✅ **Success:** Refund initiated, seats freed
- ❌ **Too Late:** `400 Bad Request` with message
- ❌ **Invalid State:** `400 Bad Request` (cannot cancel non-confirmed booking)

**Refund Processing:**
- Gateway refund is asynchronous (3-7 business days)
- System immediately marks payment as `REFUNDED`
- User receives confirmation email

---

### Flow F: Showtime Expiry (The Gatekeeper)

**Trigger:** Cron Job (Every 1 hour) or Lazy Check on Booking Request

**Purpose:** Mark past showtimes as inactive and prevent new bookings

**Sequence:**

```mermaid
sequenceDiagram
    participant Cron
    participant DB
    
    Cron->>DB: SELECT showtimes WHERE start_time < now() AND status = ACTIVE
    
    loop For Each Past Showtime
        Cron->>DB: BEGIN TRANSACTION
        Cron->>DB: UPDATE showtime → INACTIVE
        Cron->>DB: UPDATE related bookings (CONFIRMED → COMPLETED)
        Cron->>DB: COMMIT
    end
    
    Cron->>Cron: Log expiry metrics
```

**Transaction Boundary:**
- **Per Showtime:**
  - `UPDATE showtimes SET status = INACTIVE`
  - `UPDATE bookings SET status = COMPLETED WHERE showtime_id = ? AND status = CONFIRMED`

**Query:**
```sql
SELECT id 
FROM showtimes 
WHERE start_time < NOW() 
  AND status IN ('ACTIVE', 'PAUSED')
LIMIT 50;
```

**Seat State:**
- **No explicit seat transition needed**
- Reliance on `showtime.status` check during booking validation
- `SOLD` seats remain `SOLD` (historical record)
- `AVAILABLE` seats become unbookable via showtime guard

**Lazy Check (Performance Optimization):**
```java
// In Reserve Seats Flow (Flow A)
if (showtime.getStatus() == ACTIVE && showtime.getStartTime().isBefore(now())) {
    throw new ShowtimeExpiredException("Showtime has ended");
}
```

**Outcome:**
- ✅ **Showtime Closed:** No new bookings accepted
- 📊 **Bookings Completed:** Lifecycle ended for confirmed bookings
- ⚡ **Performance:** No enum needed, single status check sufficient

---

## 🚨 Error Handling Flows

### Flow G: Transaction Failure (The Safety Net)

**Trigger:** Database Deadlock or Constraint Violation during Reserve

**Common Scenarios:**
1. **Deadlock:** Two users reserve overlapping seats in different order
2. **Unique Constraint Violation:** Duplicate booking attempt
3. **Foreign Key Violation:** Invalid `showtime_id` or `user_id`
4. **Optimistic Locking Failure:** Concurrent seat update

**Sequence:**

```mermaid
sequenceDiagram
    participant User
    participant API
    participant DB
    
    User->>API: POST /bookings/reserve
    API->>DB: BEGIN TRANSACTION
    API->>DB: SELECT ... FOR UPDATE (sorted seats)
    
    alt Database Error
        DB-->>API: SQLException (Deadlock/Constraint)
        API->>DB: ROLLBACK (automatic via Spring)
        API->>API: Log error details
        
        alt Deadlock Detected
            API-->>User: 409 Conflict (Please retry)
        else Constraint Violation
            API-->>User: 409 Conflict (Seats unavailable)
        else Foreign Key Violation
            API-->>User: 400 Bad Request (Invalid reference)
        else Unknown Error
            API-->>User: 500 Internal Server Error
        end
    end
```

**Error Classification:**

| Exception Type                     | HTTP Status               | User Message                 | Action                 |
| ---------------------------------- | ------------------------- | ---------------------------- | ---------------------- |
| `DeadlockLoserDataAccessException` | `409 Conflict`            | "Seats locked, please retry" | Retry with backoff     |
| `DataIntegrityViolationException`  | `409 Conflict`            | "Seats no longer available"  | Select different seats |
| `ConstraintViolationException`     | `400 Bad Request`         | "Invalid booking data"       | Fix input              |
| `TransactionTimedOutException`     | `503 Service Unavailable` | "Service busy, retry"        | Retry after delay      |
| Generic `SQLException`             | `500 Internal Error`      | "System error"               | Contact support        |

**Rollback Guarantees:**
- Spring `@Transactional` ensures automatic rollback on exception
- No orphaned locks (`FOR UPDATE` released on rollback)
- No partial writes (all-or-nothing)

**Logging:**
```java
log.error("Booking reservation failed", 
    Map.of(
        "userId", userId,
        "showtimeId", showtimeId,
        "seatIds", seatIds,
        "errorType", e.getClass().getSimpleName(),
        "errorMessage", e.getMessage()
    )
);
```

**Retry Strategy (Client-Side):**
1. **Deadlock:** Exponential backoff (100ms, 200ms, 400ms)
2. **Constraint Violation:** No retry (show error)
3. **Timeout:** Linear backoff (1s, 2s, 3s)

**Outcome:**
- ✅ **Clean Failure:** No side effects, safe to retry
- 📋 **Detailed Logging:** Error traced and monitored
- 🔒 **No Orphaned Locks:** Database state consistent

---

## � Concurrency Control Flows

### Flow H: Concurrent Seat Reservation (The Race Condition)

**Trigger:** Two users attempt to book the same seat(s) simultaneously

**Purpose:** Demonstrate database-level locking prevents double-booking

**Business Context:** When concurrent requests target overlapping seat inventory, the system must ensure only one booking succeeds while maintaining data consistency and preventing deadlocks.

**Scenario:** User A and User B both attempt to book Seat A1 simultaneously

**Sequence:**

```mermaid
sequenceDiagram
    participant UserA
    participant RequestA
    participant DB
    participant RequestB
    participant UserB

    Note over UserA,UserB: Both click "Reserve" at the same time
    
    par Concurrent Requests
        UserA->>RequestA: POST /bookings (A1, A2)
        UserB->>RequestB: POST /bookings (A1, A3)
    end
    
    RequestA->>DB: BEGIN TRANSACTION
    RequestB->>DB: BEGIN TRANSACTION
    
    Note over RequestA,RequestB: Both sort seat IDs: [A1, A2] vs [A1, A3]
    
    RequestA->>DB: SELECT * FROM seats<br/>WHERE id IN ('A1','A2')<br/>FOR UPDATE
    Note over DB: Request A acquires lock on A1, A2
    
    RequestB->>DB: SELECT * FROM seats<br/>WHERE id IN ('A1','A3')<br/>FOR UPDATE
    Note over DB: Request B WAITS (blocked on A1)
    
    RequestA->>DB: UPDATE seats SET status='HELD'<br/>WHERE id IN ('A1','A2')
    RequestA->>DB: INSERT INTO bookings (...)
    RequestA->>DB: COMMIT
    Note over DB: Lock released on A1, A2
    
    RequestA-->>UserA: 201 Created ✅
    
    Note over DB: Request B now acquires lock
    RequestB->>DB: Check seat status
    
    alt A1 still AVAILABLE (impossible)
        RequestB->>DB: UPDATE seats, INSERT booking
        RequestB->>DB: COMMIT
        RequestB-->>UserB: 201 Created ✅
    else A1 now HELD (reality)
        RequestB->>DB: ROLLBACK
        RequestB-->>UserB: 409 SEAT_ALREADY_BOOKED ❌
    end
```

**Critical Implementation Details:**

1. **Sorted Lock Acquisition (Deadlock Prevention):**
```java
// ALWAYS sort seat IDs before locking
List<String> sortedSeatIds = seatIds.stream()
    .sorted()
    .collect(Collectors.toList());

// Query uses sorted list
SELECT * FROM seats 
WHERE id IN (?, ?, ?) -- sorted parameters
FOR UPDATE;
```

**Why Sort?**
- Request A: Wants [A3, A1] → Sorted to [A1, A3]
- Request B: Wants [A1, A2] → Sorted to [A1, A2]
- Both lock A1 first → No circular wait → No deadlock

2. **FOR UPDATE Behavior:**
```sql
-- PostgreSQL lock modes
SELECT ... FOR UPDATE;          -- Exclusive lock, blocks all other FOR UPDATE
SELECT ... FOR UPDATE NOWAIT;   -- Fails immediately if locked (alternative)
SELECT ... FOR UPDATE SKIP LOCKED; -- Skips locked rows (not suitable for bookings)
```

**We use FOR UPDATE (blocking) because:**
- User expects "first-come, first-served" fairness
- `NOWAIT` would force client-side retry loops
- `SKIP LOCKED` would silently skip requested seats (data integrity violation)

3. **Lock Holding Time:**
```
Lock Acquisition → Seat Update → Booking Insert → COMMIT
Total: ~10-50ms (single-datacenter)
```

**Transaction Isolation Level:**
```sql
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;  -- Default
```

- `READ COMMITTED` is sufficient (we use explicit locks)
- `SERIALIZABLE` would be overkill (performance penalty)

**Edge Cases Handled:**

| Scenario                             | Outcome                                  |
| ------------------------------------ | ---------------------------------------- |
| 3+ concurrent requests for same seat | Queued serially, first wins, others fail |
| Deadlock (circular wait)             | Prevented by sorted lock acquisition     |
| Request A crashes before COMMIT      | Postgres auto-rollback, lock released    |
| Request B timeout while waiting      | Connection pool timeout → 503 error      |

**Concurrency Properties:**

The system handles concurrent booking requests using PostgreSQL's `SELECT ... FOR UPDATE` pessimistic locking. When multiple requests target the same seats:

1. **Lock Acquisition:** First request to acquire row-level lock proceeds immediately
2. **Blocking:** Subsequent requests wait for lock release (typically <50ms)
3. **Validation:** Upon lock acquisition, request validates seat availability
4. **Resolution:** If seat is unavailable (HELD/SOLD), transaction rolls back with 409 Conflict

**Deadlock Prevention:** Sorting seat IDs before locking ensures consistent lock acquisition order across all requests, eliminating circular wait conditions.

**Outcome:**
- ✅ **Correctness:** Only one booking succeeds (no double-booking)
- ✅ **Performance:** Minimal lock holding time (~25ms)
- ✅ **Fairness:** First request to acquire lock wins
- ✅ **No Deadlocks:** Sorted lock acquisition guarantees

---

## 🎓 Design Principles

1. **Atomic Transitions:** All state changes within transaction boundaries
2. **Sorted Locking:** Prevents deadlocks via consistent lock ordering
3. **Idempotent Webhooks:** Safe to replay without side effects
4. **Graceful Degradation:** Sync checks compensate for async delays
5. **Clean Failures:** Rollbacks guarantee no partial state
6. **Performance Over Enums:** Showtime status check > seat state enums
7. **Explicit Boundaries:** Every flow documents transaction scope

---

## 🔍 Cross-Reference

- **State Definitions:** See `lifecycle_states.md`
- **API Contracts:** See `api_contracts.md` (future)
- **Database Schema:** See `database_schema.md` (future)
- **Error Catalog:** See `error_handling.md` (future)