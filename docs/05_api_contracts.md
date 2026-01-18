# TicketLedger: API Contracts & Standards

## 📋 Purpose

This document defines the **HTTP API contracts** for TicketLedger. It serves as the **interface specification** between frontend and backend systems.

### ✅ This file contains:
- REST endpoint definitions
- Request/Response schemas (DTOs)
- HTTP status codes
- Header requirements
- Pagination standards
- Global response envelope format

### ❌ This file does NOT contain:
- Implementation details (see sequence flows)
- Database queries or business logic
- Authentication/Authorization logic (covered separately)
- Base URLs (environment-specific)

---

## 🌐 API Conventions

### Base URL Pattern

All endpoints assume the `/api/v1` prefix:

```
POST   /api/v1/bookings
GET    /api/v1/bookings/{id}
GET    /api/v1/showtimes
```

**Environment-Specific Base URLs:**
- **Local:** `http://localhost:8080/api/v1`
- **Staging:** `https://api-staging.ticketledger.com/api/v1`
- **Production:** `https://api.ticketledger.com/api/v1`

> **Note:** This document omits base URLs for brevity. All paths are relative to `/api/v1`.

---

## 🔐 Global Standards

### 1. Idempotency

**Header:** `Idempotency-Key` (UUID recommended)

**Required For:**
- ✅ `POST` requests (creates resources)
- ✅ `PUT`/`PATCH` requests (updates critical state)
- ❌ `GET` requests (naturally idempotent)
- ⚠️ `DELETE` requests (optional, soft deletes are idempotent)

**Behavior:**
- If client sends same `Idempotency-Key` twice, server returns cached response from first successful request
- Server stores key for 24 hours after first successful response
- Different request body with same key returns `409 Conflict` with error code `IDEMPOTENCY_CONFLICT`

**Example:**
```http
POST /api/v1/bookings
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "showtimeId": "019535d9-3df7-79fb-b466-fa907fa17f9e",
  "seatIds": ["seat-uuid-1", "seat-uuid-2"]
}
```

**Cache Key Structure:**
```
idempotency:{userId}:{idempotencyKey} → {statusCode, responseBody, expiresAt}
```

---

### 2. Response Envelope

**All responses** use a consistent envelope structure:

#### Success Response
```json
{
  "success": true,
  "data": {
    // Actual response payload
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z",
    "requestId": "req-abc-123-def-456"
  }
}
```

#### Error Response
```json
{
  "success": false,
  "error": {
    "code": "SEAT_ALREADY_BOOKED",
    "message": "Some seats are no longer available.",
    "context": {
      "rejectedSeatIds": ["seat-uuid-5"]
    },
    "requestId": "req-abc-123-def-456"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

**Field Definitions:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | ✅ | `true` for 2xx, `false` for 4xx/5xx |
| `data` | `object` | ✅ (success) | Response payload |
| `error` | `object` | ✅ (failure) | Error details (see Error Handling doc) |
| `meta.timestamp` | `ISO-8601` | ✅ | Server response time (UTC) |
| `meta.requestId` | `string` | ✅ | Unique request identifier for debugging |
| `meta.pagination` | `object` | ⚠️ | Only for paginated endpoints |

---

### 3. Pagination

**Standard Pagination (Offset-Based)**

Used for finite datasets where users expect total counts (e.g., user booking history).

**Request Parameters:**
```http
GET /api/v1/bookings?page=0&size=20
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `integer` | `0` | Zero-based page number |
| `size` | `integer` | `20` | Items per page (max: 100) |

**Response Structure:**
```json
{
  "success": true,
  "data": {
    "items": [
      { "bookingId": "...", "status": "CONFIRMED" }
    ]
  },
  "meta": {
    "pagination": {
      "page": 0,
      "size": 20,
      "hasMore": true,
      "totalElements": 150  // Optional: null if expensive to compute
    },
    "timestamp": "2026-01-18T10:30:00Z",
    "requestId": "req-xyz"
  }
}
```

**Pagination Field Definitions:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `page` | `integer` | ✅ | Current page (zero-based) |
| `size` | `integer` | ✅ | Items per page |
| `hasMore` | `boolean` | ✅ | Whether more pages exist |
| `totalElements` | `integer\|null` | ⚠️ | Total count (may be `null` if expensive) |

**When to Omit `totalElements`:**
- Large datasets where `COUNT(*)` is expensive (>1 million rows)
- Infinite scroll UIs that only need `hasMore`
- Real-time feeds where count changes rapidly

**Phase 2 (Future): Cursor Pagination**
```json
{
  "meta": {
    "pagination": {
      "nextCursor": "eyJpZCI6IjAxOTUzNWQ5In0=",
      "hasMore": true
    }
  }
}
```

---

## 📋 Core Endpoints

### 1. Reserve Seats (Create Booking)

**Endpoint:** `POST /bookings`

**Purpose:** Lock seats, create booking, initiate payment

**Headers:**
```http
Authorization: Bearer {jwt_token}
Idempotency-Key: {uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "showtimeId": "019535d9-3df7-79fb-b466-fa907fa17f9e",
  "seatIds": [
    "seat-uuid-1",
    "seat-uuid-2",
    "seat-uuid-3"
  ]
}
```

**Request Schema:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `showtimeId` | `string (UUID)` | ✅ | Valid showtime ID | Which showtime to book |
| `seatIds` | `array<string>` | ✅ | 1-10 seats, all must be AVAILABLE | Seats to reserve |

**Success Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "bookingId": "booking-uuid-123",
    "status": "HELD",
    "expiresAt": "2026-01-18T10:40:00Z",
    "seats": [
      {
        "seatId": "seat-uuid-1",
        "row": "A",
        "number": "12",
        "tier": "VIP"
      }
    ],
    "amount": {
      "total": 50.00,
      "currency": "USD",
      "breakdown": [
        { "seatId": "seat-uuid-1", "price": 25.00 },
        { "seatId": "seat-uuid-2", "price": 25.00 }
      ]
    },
    "payment": {
      "paymentId": "payment-uuid-456",
      "provider": "STRIPE",
      "clientSecret": "pi_3ABC123_secret_XYZ",
      "redirectUrl": "https://checkout.stripe.com/pay/cs_test_abc123"
    }
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z",
    "requestId": "req-reserve-001"
  }
}
```

**Response Schema:**

| Field | Type | Description |
|-------|------|-------------|
| `bookingId` | `string (UUID)` | Internal booking identifier |
| `status` | `enum` | Always `HELD` on creation |
| `expiresAt` | `ISO-8601` | Hold expiry time (created_at + 10 min) |
| `seats` | `array<SeatDetails>` | Reserved seat information |
| `amount.total` | `decimal` | Total payment amount |
| `amount.currency` | `string` | ISO 4217 currency code |
| `amount.breakdown` | `array` | Per-seat pricing |
| `payment.paymentId` | `string (UUID)` | Internal payment identifier |
| `payment.provider` | `enum` | `STRIPE`, `RAZORPAY`, etc. |
| `payment.clientSecret` | `string` | **Primary:** SDK token for frontend |
| `payment.redirectUrl` | `string (URL)` | **Fallback:** Hosted payment page |

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| `400` | `INVALID_REQUEST` | Missing/invalid fields |
| `400` | `SHOWTIME_CLOSED` | Showtime not ACTIVE or expired |
| `400` | `MAX_SEATS_EXCEEDED` | Requested >10 seats |
| `409` | `SEAT_ALREADY_BOOKED` | One or more seats unavailable |
| `409` | `IDEMPOTENCY_CONFLICT` | Same key, different payload |

**Example Error:**
```json
{
  "success": false,
  "error": {
    "code": "SEAT_ALREADY_BOOKED",
    "message": "Some seats are no longer available.",
    "context": {
      "rejectedSeatIds": ["seat-uuid-2"],
      "availableSeats": 45
    },
    "requestId": "req-reserve-001"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

---

### 2. Retry Payment for Existing Booking

**Endpoint:** `POST /bookings/{bookingId}/payment-intents`

**Purpose:** Initiate a new payment attempt for an existing HELD booking without losing the seat hold

**Why Needed:** Handles payment retry scenarios when:
- Initial payment card is declined
- User wants to try a different payment method
- Booking is still HELD (within 10-minute window)

**Headers:**
```http
Authorization: Bearer {jwt_token}
Idempotency-Key: {uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "paymentMethod": "CARD"  // Optional: For analytics
}
```

**Success Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "bookingId": "booking-uuid-123",
    "status": "HELD",
    "expiresAt": "2026-01-18T10:40:00Z",
    "payment": {
      "paymentId": "payment-uuid-789",
      "provider": "STRIPE",
      "clientSecret": "pi_3DEF456_secret_ABC",
      "redirectUrl": "https://checkout.stripe.com/pay/cs_test_def456",
      "attemptNumber": 2
    }
  },
  "meta": {
    "timestamp": "2026-01-18T10:33:00Z",
    "requestId": "req-retry-001"
  }
}
```

**Response Schema:**

| Field | Type | Description |
|-------|------|-------------|
| `payment.paymentId` | `string (UUID)` | New payment record identifier |
| `payment.attemptNumber` | `integer` | Payment attempt counter (for analytics) |
| `expiresAt` | `ISO-8601` | Original hold expiry (not extended on retry) |

**Business Rules:**
- Creates a new `payments` row (allowing 1:N relationship)
- Does NOT extend the `locked_until` timestamp
- Booking remains `HELD` while payment is `PENDING`
- If previous payment was `PENDING`, mark it `FAILED` first
- Maximum 3 payment attempts per booking

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| `400` | `BOOKING_EXPIRED` | Hold window expired, seats released |
| `400` | `INVALID_BOOKING_STATUS` | Booking not in HELD state |
| `400` | `MAX_PAYMENT_ATTEMPTS` | Exceeded 3 payment attempts |
| `404` | `BOOKING_NOT_FOUND` | Invalid booking ID |
| `409` | `PAYMENT_IN_PROGRESS` | Previous payment still PENDING |

**Example Error:**
```json
{
  "success": false,
  "error": {
    "code": "MAX_PAYMENT_ATTEMPTS",
    "message": "Maximum payment attempts exceeded for this booking.",
    "context": {
      "bookingId": "booking-uuid-123",
      "attemptsMade": 3,
      "maxAttempts": 3
    },
    "requestId": "req-retry-002"
  },
  "meta": {
    "timestamp": "2026-01-18T10:35:00Z"
  }
}
```

---

### 3. Confirm Booking (Manual Sync)

**Endpoint:** `POST /bookings/{bookingId}/confirm`

**Purpose:** Force backend sync with payment gateway after SDK reports success

**Why Needed:** Addresses webhook delay - frontend triggers this after payment SDK completes

**Headers:**
```http
Authorization: Bearer {jwt_token}
Idempotency-Key: {uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "paymentIntentId": "pi_3ABC123"  // Optional: For validation
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "bookingId": "booking-uuid-123",
    "status": "CONFIRMED",
    "confirmedAt": "2026-01-18T10:35:00Z",
    "showtime": {
      "movieTitle": "Inception",
      "startTime": "2026-01-18T18:00:00Z",
      "screen": "Screen 1"
    },
    "seats": [
      {
        "row": "A",
        "number": "12",
        "tier": "VIP"
      }
    ],
    "ticket": {
      "qrCode": "data:image/png;base64,iVBORw0KGgoAAAANS...",
      "ticketNumber": "TKT-2026-001234"
    },
    "payment": {
      "paymentId": "payment-uuid-456",
      "status": "SUCCESS",
      "amount": 50.00,
      "capturedAt": "2026-01-18T10:35:00Z"
    }
  },
  "meta": {
    "timestamp": "2026-01-18T10:35:00Z",
    "requestId": "req-confirm-001"
  }
}
```

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| `400` | `BOOKING_EXPIRED` | Hold window expired, seats released |
| `402` | `PAYMENT_PENDING` | Payment not yet confirmed by gateway |
| `402` | `PAYMENT_DECLINED` | Payment failed |
| `404` | `BOOKING_NOT_FOUND` | Invalid booking ID |
| `409` | `ALREADY_CONFIRMED` | Booking already confirmed (idempotent) |

---

### 3. Cancel Booking

**Endpoint:** `POST /bookings/{bookingId}/cancel`

**Purpose:** User-initiated cancellation with refund (if eligible)

**Business Rule:** Allowed only if `showtime.startTime - now() >= 3 hours`

**Headers:**
```http
Authorization: Bearer {jwt_token}
Idempotency-Key: {uuid}
Content-Type: application/json
```

**Request Body:**
```json
{
  "reason": "CHANGE_OF_PLANS"  // Optional: For analytics
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "bookingId": "booking-uuid-123",
    "status": "CANCELLED",
    "cancelledAt": "2026-01-18T12:00:00Z",
    "refund": {
      "amount": 50.00,
      "currency": "USD",
      "status": "PROCESSING",
      "estimatedDays": "3-7 business days"
    }
  },
  "meta": {
    "timestamp": "2026-01-18T12:00:00Z",
    "requestId": "req-cancel-001"
  }
}
```

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| `400` | `CANCELLATION_TOO_LATE` | Less than 3 hours before showtime |
| `400` | `INVALID_STATUS` | Booking not in CONFIRMED state |
| `404` | `BOOKING_NOT_FOUND` | Invalid booking ID |
| `409` | `ALREADY_CANCELLED` | Booking already cancelled |

---

### 4. Get Booking Details

**Endpoint:** `GET /bookings/{bookingId}`

**Purpose:** Retrieve single booking with full details

**Headers:**
```http
Authorization: Bearer {jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "bookingId": "booking-uuid-123",
    "status": "CONFIRMED",
    "createdAt": "2026-01-18T10:30:00Z",
    "confirmedAt": "2026-01-18T10:35:00Z",
    "showtime": {
      "showtimeId": "showtime-uuid",
      "movieTitle": "Inception",
      "startTime": "2026-01-18T18:00:00Z",
      "screen": "Screen 1"
    },
    "seats": [
      {
        "seatId": "seat-uuid-1",
        "row": "A",
        "number": "12",
        "tier": "VIP",
        "price": 25.00
      }
    ],
    "amount": {
      "total": 50.00,
      "currency": "USD"
    },
    "payment": {
      "paymentId": "payment-uuid-456",
      "status": "SUCCESS",
      "method": "CREDIT_CARD",
      "capturedAt": "2026-01-18T10:35:00Z"
    },
    "ticket": {
      "qrCode": "data:image/png;base64,...",
      "ticketNumber": "TKT-2026-001234"
    }
  },
  "meta": {
    "timestamp": "2026-01-18T14:00:00Z",
    "requestId": "req-get-001"
  }
}
```

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| `403` | `FORBIDDEN` | User doesn't own this booking |
| `404` | `BOOKING_NOT_FOUND` | Invalid booking ID |

---

### 5. List User Bookings (History)

**Endpoint:** `GET /bookings`

**Purpose:** Retrieve user's booking history with pagination

**Headers:**
```http
Authorization: Bearer {jwt_token}
```

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `integer` | `0` | Zero-based page number |
| `size` | `integer` | `20` | Items per page (max: 100) |
| `status` | `enum` | `null` | Filter by status (CONFIRMED, CANCELLED, etc.) |
| `sortBy` | `enum` | `createdAt` | Sort field (createdAt, showtimeDate) |
| `sortOrder` | `enum` | `DESC` | ASC or DESC |

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "bookingId": "booking-uuid-123",
        "status": "CONFIRMED",
        "createdAt": "2026-01-18T10:30:00Z",
        "showtime": {
          "movieTitle": "Inception",
          "startTime": "2026-01-18T18:00:00Z",
          "screen": "Screen 1"
        },
        "seatsCount": 2,
        "totalAmount": 50.00,
        "currency": "USD"
      }
    ]
  },
  "meta": {
    "pagination": {
      "page": 0,
      "size": 20,
      "hasMore": false,
      "totalElements": 15
    },
    "timestamp": "2026-01-18T14:00:00Z",
    "requestId": "req-list-001"
  }
}
```

**Response Notes:**
- `items` contains `BookingSummaryDTO` (minimal fields for list view)
- `totalElements` is included because user booking count is typically small (<1000)
- Use `GET /bookings/{id}` for full details

---

### 6. Get Available Seats

**Endpoint:** `GET /seats`

**Purpose:** Retrieve available seats for a showtime

**Headers:**
```http
Authorization: Bearer {jwt_token}  // Optional for browsing
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `showtimeId` | `string (UUID)` | ✅ | Which showtime |
| `status` | `enum` | ❌ | Filter by status (default: AVAILABLE) |

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "showtimeId": "showtime-uuid",
    "totalSeats": 100,
    "availableCount": 45,
    "seats": [
      {
        "seatId": "seat-uuid-1",
        "row": "A",
        "number": "1",
        "tier": "VIP",
        "status": "AVAILABLE",
        "price": 25.00
      },
      {
        "seatId": "seat-uuid-2",
        "row": "A",
        "number": "2",
        "tier": "VIP",
        "status": "SOLD",
        "price": 25.00
      }
    ],
    "tiers": [
      {
        "tierId": "tier-uuid-1",
        "name": "VIP",
        "basePrice": 25.00,
        "availableCount": 10
      },
      {
        "tierId": "tier-uuid-2",
        "name": "Regular",
        "basePrice": 15.00,
        "availableCount": 35
      }
    ]
  },
  "meta": {
    "timestamp": "2026-01-18T14:00:00Z",
    "requestId": "req-seats-001"
  }
}
```

**Response Notes:**
- No pagination needed (fixed seat count per showtime, typically <500)
- `status` field allows frontend to render seat map
- `availableCount` helps with UI messaging

---

### 7. List Showtimes

**Endpoint:** `GET /showtimes`

**Purpose:** Browse available showtimes (public endpoint)

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `movieId` | `string (UUID)` | `null` | Filter by movie |
| `date` | `ISO-8601 date` | `today` | Filter by date (YYYY-MM-DD) |
| `screenId` | `string (UUID)` | `null` | Filter by screen |
| `page` | `integer` | `0` | Zero-based page |
| `size` | `integer` | `20` | Items per page |

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "showtimeId": "showtime-uuid-1",
        "movie": {
          "movieId": "movie-uuid",
          "title": "Inception",
          "durationMinutes": 148
        },
        "screen": {
          "screenId": "screen-uuid",
          "name": "Screen 1"
        },
        "startTime": "2026-01-18T18:00:00Z",
        "endTime": "2026-01-18T20:28:00Z",
        "status": "ACTIVE",
        "availableSeats": 45,
        "totalSeats": 100,
        "pricing": {
          "minPrice": 15.00,
          "maxPrice": 25.00,
          "currency": "USD"
        }
      }
    ]
  },
  "meta": {
    "pagination": {
      "page": 0,
      "size": 20,
      "hasMore": true,
      "totalElements": null  // Expensive query, omitted
    },
    "timestamp": "2026-01-18T14:00:00Z",
    "requestId": "req-showtimes-001"
  }
}
```

**Response Notes:**
- `totalElements` is `null` because showtime count can be expensive
- Frontend uses `hasMore` for infinite scroll
- Public endpoint (no authentication required for browsing)

---

## 🎨 DTO Naming Conventions

### Request DTOs
```java
CreateBookingRequest
ConfirmBookingRequest
CancelBookingRequest
```

### Response DTOs
```java
BookingResponse              // Full details (GET /bookings/{id})
BookingSummaryResponse       // List view (GET /bookings)
BookingConfirmationResponse  // After confirmation (with QR code)
```

### Nested DTOs
```java
PaymentDetails
SeatDetails
AmountDetails
ShowtimeDetails
TicketDetails
```

---

## 🔒 Security Guidelines

### 1. Never Expose Entities
```java
// ❌ BAD: Exposes internal fields
@GetMapping("/bookings/{id}")
public Booking getBooking(@PathVariable UUID id) {
    return bookingRepository.findById(id);
}

// ✅ GOOD: Controlled DTO
@GetMapping("/bookings/{id}")
public BookingResponse getBooking(@PathVariable UUID id) {
    return bookingService.getBooking(id).toResponse();
}
```

### 2. Sensitive Fields to Exclude
- `payment.provider_response` (gateway debug data)
- `payment.provider_transaction_id` (unless needed for support)
- `user.password_hash` (never expose)
- Internal audit fields (`version`, `updated_at` in entities)

### 3. Authorization Checks
- User can only access their own bookings
- Admin endpoints require `role = ADMIN`
- Soft-deleted resources return `404` (not exposed to users)

---

## 📊 HTTP Status Code Guide

| Status | Usage | Example |
|--------|-------|---------|
| `200 OK` | Successful read or update | GET booking, POST confirm |
| `201 Created` | Resource created | POST /bookings |
| `400 Bad Request` | Validation error | Invalid seat IDs |
| `401 Unauthorized` | Missing/invalid auth token | No JWT |
| `403 Forbidden` | Valid token, insufficient permissions | User accessing admin endpoint |
| `404 Not Found` | Resource doesn't exist | Invalid booking ID |
| `409 Conflict` | Business rule violation | Seat already booked |
| `422 Unprocessable Entity` | Semantic error | Booking expired showtime |
| `500 Internal Server Error` | Unhandled exception | Database down |
| `503 Service Unavailable` | Downstream service down | Payment gateway timeout |

---

## 🔍 Cross-Reference

- **State Machines:** See `02_lifecycle_states.md`
- **Transaction Flows:** See `03_sequence_flows.md`
- **Database Schema:** See `04_database_schema.md`
- **Error Handling:** See `06_error_handling.md`

---

## ✅ Contract Lock Checklist

- [x] Idempotency strategy defined (POST/PUT/PATCH only)
- [x] Response envelope standardized
- [x] Pagination strategy defined (conditional totalElements)
- [x] Payment approach clarified (SDK + redirect fallback)
- [x] DTO-first strategy enforced
- [x] All critical endpoints documented
- [x] Error responses defined
- [x] Security guidelines established