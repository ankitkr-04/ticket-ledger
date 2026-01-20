# TicketLedger: Admin API Contracts (Control Plane)

## 📋 Purpose

This document defines the **privileged Control Plane API** for TicketLedger. These endpoints are strictly for **Theater Managers** and **System Admins**.

### 🔒 Security Standards

* **Base URL:** `/api/v1/admin`
* **Auth:** Requires `Authorization: Bearer <admin-jwt>` with `role = ADMIN`
* **Scope Check:** All `WRITE` operations automatically enforce **Theater Scope**
    * **Invariant:** Admin must have an entry in `admin_theater_access` for the target resource's theater
    * **Failure:** Returns `403 THEATER_ACCESS_DENIED` immediately (no DB locks acquired)

### ✅ This file contains:

- Theater management endpoints (create, list)
- Inventory management (screens, showtimes)
- Financial operations (refunds)
- Admin-specific error codes

### ❌ This file does NOT contain:

- Customer-facing endpoints (see `05_api_contracts.md`)
- Implementation details (see service layer)
- Authorization logic (see `AdminAuthorizationService`)

---

## 🏢 Theater Management

### 1. Create Theater

**Endpoint:** `POST /admin/theaters`

**Purpose:** Register a new physical theater location. Automatically grants access to the creating admin.

**Authorization:** Requires `role = ADMIN`

**Headers:**
```http
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "PVR Phoenix",
  "city": "Mumbai",
  "address": "High Street Phoenix, Lower Parel"
}
```

**Request Schema:**

| Field     | Type     | Required | Constraints | Description                    |
| --------- | -------- | -------- | ----------- | ------------------------------ |
| `name`    | `string` | ✅        | 1-255 chars | Theater name                   |
| `city`    | `string` | ✅        | 1-100 chars | City location                  |
| `address` | `string` | ❌        | 0-500 chars | Full street address (optional) |

**Success Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "theaterId": "theater-uuid-123",
    "name": "PVR Phoenix",
    "city": "Mumbai",
    "address": "High Street Phoenix, Lower Parel",
    "createdAt": "2026-01-20T10:00:00Z"
  },
  "meta": {
    "timestamp": "2026-01-20T10:00:00Z",
    "requestId": "req-create-theater-001",
    "message": "Theater created and access granted to admin"
  }
}
```

**Error Responses:**

| Status | Error Code                 | Description                    |
| ------ | -------------------------- | ------------------------------ |
| `400`  | `INVALID_REQUEST`          | Missing required fields        |
| `401`  | `UNAUTHORIZED`             | Missing or invalid admin token |
| `403`  | `INSUFFICIENT_PERMISSIONS` | User is not an admin           |
| `409`  | `THEATER_ALREADY_EXISTS`   | Theater with name+city exists  |

**Business Rules:**
- Creating admin is **automatically granted access** via `admin_theater_access` table
- Theater name + city combination must be unique
- Address is optional for MVP

---

### 2. List My Theaters

**Endpoint:** `GET /admin/theaters`

**Purpose:** List all theaters the authenticated admin has access to.

**Authorization:** Requires `role = ADMIN`

**Headers:**
```http
Authorization: Bearer <admin-jwt>
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "theaterId": "theater-uuid-123",
      "name": "PVR Phoenix",
      "city": "Mumbai",
      "screenCount": 8,
      "grantedAt": "2026-01-20T10:00:00Z"
    },
    {
      "theaterId": "theater-uuid-456",
      "name": "INOX Nariman Point",
      "city": "Mumbai",
      "screenCount": 5,
      "grantedAt": "2026-01-15T14:30:00Z"
    }
  ],
  "meta": {
    "total": 2,
    "timestamp": "2026-01-20T11:00:00Z"
  }
}
```

---

## 🎬 Inventory Management

### 1. Create Screen

**Endpoint:** `POST /admin/theaters/{theaterId}/screens`

**Purpose:** Create a new screen within a theater.

**Authorization:** Requires `role = ADMIN` + theater access

**Scope Check:**
```
1. Resolve theaterId from path parameter
2. Query admin_theater_access: WHERE user_id = :adminId AND theater_id = :theaterId AND revoked_at IS NULL
3. If not found → 403 THEATER_ACCESS_DENIED
4. If found → proceed with screen creation
```

**Headers:**
```http
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

**Path Parameters:**

| Parameter   | Type     | Required | Description     |
| ----------- | -------- | -------- | --------------- |
| `theaterId` | `string` | ✅        | UUID of theater |

**Request Body:**
```json
{
  "name": "Audi 1",
  "totalSeats": 180
}
```

**Request Schema:**

| Field        | Type      | Required | Constraints | Description      |
| ------------ | --------- | -------- | ----------- | ---------------- |
| `name`       | `string`  | ✅        | 1-50 chars  | Screen name      |
| `totalSeats` | `integer` | ✅        | 1-1000      | Seating capacity |

**Success Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "screenId": "screen-uuid-789",
    "theaterId": "theater-uuid-123",
    "name": "Audi 1",
    "totalSeats": 180,
    "createdAt": "2026-01-20T12:00:00Z"
  },
  "meta": {
    "timestamp": "2026-01-20T12:00:00Z",
    "requestId": "req-create-screen-001"
  }
}
```

**Error Responses:**

| Status | Error Code              | Description                                     |
| ------ | ----------------------- | ----------------------------------------------- |
| `400`  | `INVALID_REQUEST`       | Missing required fields                         |
| `401`  | `UNAUTHORIZED`          | Missing or invalid admin token                  |
| `403`  | `THEATER_ACCESS_DENIED` | Admin does not have access to this theater      |
| `404`  | `THEATER_NOT_FOUND`     | Theater does not exist                          |
| `409`  | `SCREEN_ALREADY_EXISTS` | Screen with this name already exists in theater |

---

### 2. Create Showtime

**Endpoint:** `POST /admin/screens/{screenId}/showtimes`

**Purpose:** Schedule a new movie screening.

**Authorization:** Resolves `screenId → theaterId` and checks theater access

**Scope Check:**
```
1. Resolve screenId from path parameter
2. Query screens table: SELECT theater_id FROM screens WHERE id = :screenId
3. Query admin_theater_access for resolved theater_id
4. If no access → 403 THEATER_ACCESS_DENIED
```

**Headers:**
```http
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

**Path Parameters:**

| Parameter  | Type     | Required | Description    |
| ---------- | -------- | -------- | -------------- |
| `screenId` | `string` | ✅        | UUID of screen |

**Request Body:**
```json
{
  "movieId": "movie-uuid-456",
  "startTime": "2026-01-25T18:00:00Z"
}
```

**Request Schema:**

| Field       | Type       | Required | Description               |
| ----------- | ---------- | -------- | ------------------------- |
| `movieId`   | `string`   | ✅        | UUID of movie             |
| `startTime` | `ISO-8601` | ✅        | Screening start timestamp |

**Success Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "showtimeId": "showtime-uuid-012",
    "screenId": "screen-uuid-789",
    "movieId": "movie-uuid-456",
    "startTime": "2026-01-25T18:00:00Z",
    "endTime": "2026-01-25T20:30:00Z",
    "status": "ACTIVE",
    "createdAt": "2026-01-20T12:15:00Z"
  },
  "meta": {
    "timestamp": "2026-01-20T12:15:00Z"
  }
}
```

**Error Responses:**

| Status | Error Code              | Description                             |
| ------ | ----------------------- | --------------------------------------- |
| `400`  | `INVALID_REQUEST`       | Missing required fields                 |
| `401`  | `UNAUTHORIZED`          | Missing or invalid admin token          |
| `403`  | `THEATER_ACCESS_DENIED` | Admin does not manage this screen       |
| `404`  | `SCREEN_NOT_FOUND`      | Screen does not exist                   |
| `404`  | `MOVIE_NOT_FOUND`       | Movie does not exist                    |
| `409`  | `SCREEN_CONFLICT`       | Screen already has overlapping showtime |

**Business Rules:**
- `endTime` is automatically calculated: `startTime + movie.duration_minutes`
- Database exclusion constraint prevents overlapping showtimes on same screen
- Showtime starts in `ACTIVE` status (bookable)

---

### 3. Pause Showtime (Kill Switch)

**Endpoint:** `PATCH /admin/showtimes/{showtimeId}/status`

**Purpose:** Pause or reactivate a showtime. Pausing triggers automatic cleanup of pending bookings.

**Authorization:** Resolves `showtimeId → screenId → theaterId` and checks theater access

**Scope Check:**
```
1. Resolve showtimeId from path parameter
2. Query showtimes → screens: SELECT theater_id FROM screens WHERE id = (SELECT screen_id FROM showtimes WHERE id = :showtimeId)
3. Query admin_theater_access for resolved theater_id
4. If no access → 403 THEATER_ACCESS_DENIED
```

**Headers:**
```http
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

**Path Parameters:**

| Parameter    | Type     | Required | Description      |
| ------------ | -------- | -------- | ---------------- |
| `showtimeId` | `string` | ✅        | UUID of showtime |

**Request Body:**
```json
{
  "status": "PAUSED"
}
```

**Request Schema:**

| Field    | Type   | Required | Constraints          | Description         |
| -------- | ------ | -------- | -------------------- | ------------------- |
| `status` | `enum` | ✅        | `ACTIVE` or `PAUSED` | New showtime status |

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "showtimeId": "showtime-uuid-012",
    "status": "PAUSED",
    "updatedAt": "2026-01-20T13:00:00Z",
    "updatedBy": {
      "adminId": "admin-uuid-789",
      "email": "admin@ticketledger.com"
    },
    "affectedBookings": {
      "totalExpired": 12,
      "seatsReleased": 45
    }
  },
  "meta": {
    "timestamp": "2026-01-20T13:00:00Z"
  }
}
```

**Error Responses:**

| Status | Error Code                | Description                                    |
| ------ | ------------------------- | ---------------------------------------------- |
| `400`  | `INVALID_REQUEST`         | Invalid status value                           |
| `401`  | `UNAUTHORIZED`            | Missing or invalid admin token                 |
| `403`  | `THEATER_ACCESS_DENIED`   | Admin does not manage this showtime            |
| `404`  | `SHOWTIME_NOT_FOUND`      | Showtime does not exist                        |
| `409`  | `SHOWTIME_ALREADY_PAUSED` | Attempting to pause an already paused showtime |

**Kill Switch Behavior:**

When status is set to `PAUSED`, the system atomically:
1. Updates showtime status to `PAUSED`
2. Force-expires all `HELD` bookings for this showtime
3. Releases all associated seats back to `AVAILABLE`
4. All operations occur in a single transaction (all-or-nothing)

**Use Cases:**
- Theater emergency (technical issue, power failure)
- Movie distributor cancels show
- Capacity management during peak load

---

## 💰 Financial Operations

### 1. Process Refund

**Endpoint:** `POST /admin/bookings/{bookingId}/refund`

**Purpose:** Manually refund a confirmed booking.

**Authorization:** Resolves `bookingId → showtimeId → screenId → theaterId` and checks theater access

**Scope Check:**
```
0. PRE-FLIGHT: Validate theater access BEFORE acquiring locks
   - Resolve booking → showtime → screen → theater
   - Query admin_theater_access for resolved theater_id
   - If no access → 403 THEATER_ACCESS_DENIED (immediate return, no locks)
   - If access granted → proceed to transaction
```

**Headers:**
```http
Authorization: Bearer <admin-jwt>
Idempotency-Key: admin-refund-{bookingId}-{timestamp}
Content-Type: application/json
```

**Path Parameters:**

| Parameter   | Type     | Required | Description     |
| ----------- | -------- | -------- | --------------- |
| `bookingId` | `string` | ✅        | UUID of booking |

**Request Body:**
```json
{
  "reason": "Customer complaint - show cancelled by theater"
}
```

**Request Schema:**

| Field    | Type     | Required | Constraints  | Description               |
| -------- | -------- | -------- | ------------ | ------------------------- |
| `reason` | `string` | ✅        | 10-500 chars | Audit trail justification |

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "refundId": "refund-uuid-123",
    "bookingId": "booking-uuid-456",
    "amount": 450.00,
    "status": "COMPLETED",
    "stripeRefundId": "re_1234567890",
    "processedAt": "2026-01-20T14:00:00Z",
    "processedBy": {
      "adminId": "admin-uuid-789",
      "email": "admin@ticketledger.com"
    }
  },
  "meta": {
    "timestamp": "2026-01-20T14:00:00Z",
    "requestId": "req-admin-refund-001"
  }
}
```

**Error Responses:**

| Status | Error Code                 | Description                                           |
| ------ | -------------------------- | ----------------------------------------------------- |
| `400`  | `INVALID_REQUEST`          | Missing required fields                               |
| `401`  | `UNAUTHORIZED`             | Missing or invalid admin token                        |
| `403`  | `THEATER_ACCESS_DENIED`    | Admin does not manage this booking's theater          |
| `404`  | `BOOKING_NOT_FOUND`        | Booking does not exist                                |
| `409`  | `INVALID_STATE_TRANSITION` | Booking not in refundable state (CONFIRMED/COMPLETED) |
| `409`  | `REFUND_ALREADY_PENDING`   | Another refund request is already being processed     |
| `409`  | `IDEMPOTENCY_CONFLICT`     | Same key used with different request body             |
| `423`  | `BOOKING_LOCKED`           | Booking is currently being processed                  |

**Idempotency Behavior:**
- Admin refunds are idempotent using the `Idempotency-Key` header
- If the same key is used with the same request body, the cached response is returned
- If the same key is used with a different body, a `409 IDEMPOTENCY_CONFLICT` error is returned
- Idempotency keys are stored for 24 hours after successful refund

**State Transition Rules:**
```mermaid
stateDiagram-v2
    [*] --> CONFIRMED
    [*] --> COMPLETED
    CONFIRMED --> REFUND_INITIATED: admin_refund
    REFUND_INITIATED --> REFUNDED: stripe_success
    COMPLETED --> REFUND_INITIATED: admin_refund
    
    note right of HELD: ❌ Cannot refund HELD
    note right of CANCELLED: ❌ Cannot refund CANCELLED
```

**Concurrency Guarantees:**
- Uses pessimistic row-level locking (`SELECT ... FOR UPDATE NOWAIT`)
- `REFUND_INITIATED` state acts as transient lock during Stripe API call
- Two concurrent admin refund attempts will result in one succeeding and the other receiving `423 BOOKING_LOCKED`
- If user payment is in-flight, admin receives `423 BOOKING_LOCKED` error

**Transaction Flow:**
```
0. Pre-flight: Check theater access (no locks)
1. BEGIN TRANSACTION
2. Lock booking (SELECT ... FOR UPDATE NOWAIT)
3. Validate state (must be CONFIRMED or COMPLETED)
4. Update booking status to REFUND_INITIATED
5. Create audit log entry (INITIATED)
6. COMMIT

7. Call Stripe refund API (outside transaction)

8. If Stripe succeeds:
   BEGIN TRANSACTION
   - Update booking status to REFUNDED
   - Update seats to AVAILABLE
   - Update audit log (COMPLETED)
   COMMIT

9. If Stripe fails:
   BEGIN TRANSACTION
   - Rollback booking status to CONFIRMED
   - Update audit log (FAILED)
   COMMIT
```

**Audit Trail:**
- Every refund logged to `admin_audit_log` table
- Includes: admin_user_id, booking_id, action, status, reason, idempotency_key
- Immutable records for compliance and forensics

---

## 📊 Admin-Specific Error Codes

| Code                      | HTTP Status | Message                                            | Context Fields        | When Triggered                   |
| ------------------------- | ----------- | -------------------------------------------------- | --------------------- | -------------------------------- |
| `THEATER_ACCESS_DENIED`   | 403         | You do not have permission to manage this theater. | `theaterId`           | Admin lacks theater access       |
| `THEATER_NOT_FOUND`       | 404         | Theater does not exist.                            | `theaterId`           | Invalid theater ID               |
| `THEATER_ALREADY_EXISTS`  | 409         | Theater with this name and city already exists.    | `name`, `city`        | Duplicate theater                |
| `SCREEN_ALREADY_EXISTS`   | 409         | Screen with this name already exists in theater.   | `screenName`          | Duplicate screen name in theater |
| `SCREEN_CONFLICT`         | 409         | Screen has overlapping showtime.                   | `conflictingShowtime` | Showtime scheduling conflict     |
| `SHOWTIME_ALREADY_PAUSED` | 409         | Showtime is already paused.                        | `currentStatus`       | Redundant pause operation        |
| `INVALID_REFUND_STATE`    | 409         | Booking is not in a refundable state.              | `currentState`        | Refund on HELD/EXPIRED/CANCELLED |
| `REFUND_ALREADY_PENDING`  | 409         | Another refund request is already being processed. | `existingRefundId`    | Concurrent refund attempt        |
| `BOOKING_LOCKED`          | 423         | Booking is currently being processed.              | `lockedBy`            | Concurrent modification          |

---

## 🔍 Cross-Reference

- **Customer API:** See `05_api_contracts.md` _(Data Plane)_
- **Admin Workflows:** See `07_admin_workflows.md` _(Transaction Safety)_
- **Database Schema:** See `04_database_schema.md` _(admin_theater_access, admin_audit_log)_
- **Error Handling:** See `06_error_handling.md` _(Standard Error Format)_
- **Authorization:** See Decision `006_admin_scope_and_schema.md` _(Theater-Scoped Model)_

---

## 🔐 Security Best Practices

### 1. Theater Scope Enforcement

All admin endpoints follow this authorization pattern:

```java
@PostMapping("/admin/theaters/{theaterId}/screens")
@PreAuthorize("hasRole('ADMIN')")
public ScreenResponse createScreen(
    @PathVariable UUID theaterId,
    @RequestBody CreateScreenRequest request
) {
    UUID currentAdminId = SecurityContext.getCurrentUserId();
    
    // 1. PRE-FLIGHT: Validate theater access (no DB locks)
    adminAuthService.assertTheaterAccess(currentAdminId, theaterId);
    
    // 2. Proceed with operation
    return screenService.createScreen(theaterId, request);
}
```

### 2. Audit Trail

All privileged operations are logged to `admin_audit_log`:

- **What:** Action type (REFUND, PAUSE, etc.)
- **Who:** Admin user ID and email
- **When:** Timestamp with timezone
- **Why:** Mandatory reason field
- **Target:** Explicit FK to booking/showtime/theater
- **Outcome:** Status (INITIATED, COMPLETED, FAILED)

### 3. Idempotency Keys

Financial operations require idempotency keys:

- Format: `admin-refund-{bookingId}-{timestamp}`
- Stored for 24 hours
- Prevents duplicate refunds from UI retries
- Returns cached response for duplicate keys

---

## ✅ Admin API Checklist

- [x] Theater management endpoints documented
- [x] Scope check pattern documented for all WRITE operations
- [x] Screen creation endpoint with theater scoping
- [x] Showtime creation and pause endpoints
- [x] Refund endpoint with pre-flight authorization
- [x] Admin-specific error codes defined
- [x] Audit trail requirements documented
- [x] Idempotency requirements for financial operations
- [x] Security best practices section added
- [x] Cross-references to related documentation

---

**Status:** Production-Ready  
**Last Updated:** 2026-01-20  
**Owner:** Platform Team
