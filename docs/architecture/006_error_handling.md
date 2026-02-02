# TicketLedger: Error Handling Strategy

## 📋 Purpose

This document defines the **error handling standards** for TicketLedger. It serves as the **error contract** between frontend and backend systems.

### ✅ This file contains:
- Error response format specification
- Machine-readable error code catalog
- Context field patterns
- HTTP status code mapping
- Localization strategy
- Logging requirements

### ❌ This file does NOT contain:
- Implementation details (exception handlers)
- Stack traces (internal only)
- Database error messages (abstracted)
- Third-party API errors (translated)

---

## 🎯 Error Response Format

### Standard Error Envelope

All error responses follow the **Problem Details** format (RFC 7807 inspired):

```json
{
  "success": false,
  "error": {
    "code": "SEAT_ALREADY_BOOKED",
    "message": "Some seats are no longer available.",
    "context": {
      "rejectedSeatIds": ["seat-uuid-5"],
      "availableSeats": 45
    },
    "requestId": "req-abc-123-def-456"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

### Field Definitions

| Field             | Type       | Required | Description                                              |
| ----------------- | ---------- | -------- | -------------------------------------------------------- |
| `success`         | `boolean`  | ✅        | Always `false` for errors                                |
| `error.code`      | `string`   | ✅        | Machine-readable error identifier (uppercase snake_case) |
| `error.message`   | `string`   | ✅        | Human-readable English description                       |
| `error.context`   | `object`   | ⚠️        | Additional error-specific data (optional)                |
| `error.requestId` | `string`   | ✅        | Unique request identifier for debugging                  |
| `meta.timestamp`  | `ISO-8601` | ✅        | Server error time (UTC)                                  |

---

## 📖 Error Code Catalog

### 🔴 Critical Failures (5xx)

System-level errors requiring immediate attention.

| Code                  | HTTP Status | Message                          | Context Fields             | Action                 |
| --------------------- | ----------- | -------------------------------- | -------------------------- | ---------------------- |
| `INTERNAL_ERROR`      | 500         | Internal server error occurred.  | `exceptionType` (optional) | Contact support        |
| `DATABASE_ERROR`      | 500         | Database operation failed.       | -                          | Retry, contact support |
| `DOWNSTREAM_TIMEOUT`  | 503         | Payment gateway timed out.       | `provider`, `timeoutMs`    | Retry payment          |
| `SERVICE_UNAVAILABLE` | 503         | Service temporarily unavailable. | `estimatedRecoveryTime`    | Retry later            |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "DOWNSTREAM_TIMEOUT",
    "message": "Payment gateway timed out.",
    "context": {
      "provider": "STRIPE",
      "timeoutMs": 5000
    },
    "requestId": "req-xyz-789"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

---

### 🟠 Business Validations (4xx)

Business rule violations or invalid requests.

#### Booking Errors

| Code                     | HTTP Status | Message                                   | Context Fields                      | When Triggered             |
| ------------------------ | ----------- | ----------------------------------------- | ----------------------------------- | -------------------------- |
| `SEAT_ALREADY_BOOKED`    | 409         | Some seats are no longer available.       | `rejectedSeatIds`, `availableSeats` | Race condition, seat taken |
| `SHOWTIME_CLOSED`        | 400         | Showtime is not accepting bookings.       | `showtimeId`, `status`              | Showtime not ACTIVE        |
| `SHOWTIME_EXPIRED`       | 400         | Showtime has already started.             | `showtimeId`, `startTime`           | Past showtime              |
| `MAX_SEATS_EXCEEDED`     | 400         | Maximum seat limit exceeded.              | `requestedSeats`, `maxAllowed`      | Requested >10 seats        |
| `BOOKING_EXPIRED`        | 400         | Booking hold has expired.                 | `bookingId`, `expiredAt`            | Hold timeout               |
| `BOOKING_NOT_FOUND`      | 404         | Booking does not exist.                   | `bookingId`                         | Invalid ID or soft-deleted |
| `INVALID_BOOKING_STATUS` | 400         | Operation not allowed for booking status. | `currentStatus`, `requiredStatus`   | Status mismatch            |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "SEAT_ALREADY_BOOKED",
    "message": "Some seats are no longer available.",
    "context": {
      "rejectedSeatIds": ["seat-uuid-2", "seat-uuid-5"],
      "availableSeats": 43,
      "showtimeId": "showtime-uuid-123"
    },
    "requestId": "req-book-001"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:15Z"
  }
}
```

#### Payment Errors

| Code                 | HTTP Status | Message                                 | Context Fields              | When Triggered         |
| -------------------- | ----------- | --------------------------------------- | --------------------------- | ---------------------- |
| `PAYMENT_DECLINED`   | 402         | Payment was declined by the gateway.    | `provider`, `declineReason` | Card declined          |
| `PAYMENT_FAILED`     | 402         | Payment processing failed.              | `provider`, `failureReason` | Gateway error          |
| `PAYMENT_PENDING`    | 402         | Payment is still being processed.       | `paymentId`, `status`       | Premature confirmation |
| `REFUND_FAILED`      | 500         | Refund could not be processed.          | `paymentId`, `reason`       | Gateway refund error   |
| `REFUND_NOT_ALLOWED` | 400         | Refund is not allowed for this payment. | `paymentStatus`             | Payment not SUCCESS    |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "PAYMENT_DECLINED",
    "message": "Payment was declined by the gateway.",
    "context": {
      "provider": "STRIPE",
      "declineReason": "insufficient_funds",
      "declineCode": "card_declined"
    },
    "requestId": "req-pay-002"
  },
  "meta": {
    "timestamp": "2026-01-18T10:35:00Z"
  }
}
```

#### Cancellation Errors

| Code                    | HTTP Status | Message                           | Context Fields                          | When Triggered           |
| ----------------------- | ----------- | --------------------------------- | --------------------------------------- | ------------------------ |
| `CANCELLATION_TOO_LATE` | 400         | Cancellation window has closed.   | `showtimeStartTime`, `minHoursRequired` | <3 hours before showtime |
| `ALREADY_CANCELLED`     | 409         | Booking is already cancelled.     | `cancelledAt`                           | Duplicate cancellation   |
| `CANNOT_CANCEL_EXPIRED` | 400         | Cannot cancel an expired booking. | `bookingStatus`                         | Booking expired          |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "CANCELLATION_TOO_LATE",
    "message": "Cancellation window has closed.",
    "context": {
      "showtimeStartTime": "2026-01-18T18:00:00Z",
      "currentTime": "2026-01-18T16:30:00Z",
      "minHoursRequired": 3,
      "hoursRemaining": 1.5
    },
    "requestId": "req-cancel-003"
  },
  "meta": {
    "timestamp": "2026-01-18T16:30:00Z"
  }
}
```

#### Idempotency Errors

| Code                      | HTTP Status | Message                                        | Context Fields                      | When Triggered           |
| ------------------------- | ----------- | ---------------------------------------------- | ----------------------------------- | ------------------------ |
| `IDEMPOTENCY_CONFLICT`    | 409         | Idempotency key reused with different payload. | `originalRequest`, `currentRequest` | Same key, different body |
| `IDEMPOTENCY_KEY_MISSING` | 400         | Idempotency-Key header is required.            | -                                   | POST/PUT without header  |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_CONFLICT",
    "message": "Idempotency key reused with different payload.",
    "context": {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
      "originalSeatIds": ["seat-1", "seat-2"],
      "currentSeatIds": ["seat-3", "seat-4"]
    },
    "requestId": "req-idem-001"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

---

### 🟡 Authentication & Authorization (4xx)

| Code                    | HTTP Status | Message                                             | Context Fields | When Triggered             |
| ----------------------- | ----------- | --------------------------------------------------- | -------------- | -------------------------- |
| `UNAUTHORIZED`          | 401         | Authentication required.                            | -              | Missing/invalid JWT        |
| `TOKEN_EXPIRED`         | 401         | Authentication token has expired.                   | `expiredAt`    | JWT expired                |
| `FORBIDDEN`             | 403         | You do not have permission to access this resource. | `requiredRole` | Insufficient permissions   |
| `USER_NOT_VERIFIED`     | 403         | Email verification required.                        | `email`        | Unverified email           |
| `THEATER_ACCESS_DENIED` | 403         | You do not have permission to manage this theater.  | `theaterId`    | Admin lacks theater access |
| `THEATER_NOT_FOUND`     | 404         | Theater does not exist.                             | `theaterId`    | Invalid theater ID         |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "You do not have permission to access this resource.",
    "context": {
      "requiredRole": "ADMIN",
      "userRole": "CUSTOMER"
    },
    "requestId": "req-auth-001"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

---

### 🔵 Validation Errors (4xx)

| Code                     | HTTP Status | Message                    | Context Fields           | When Triggered            |
| ------------------------ | ----------- | -------------------------- | ------------------------ | ------------------------- |
| `INVALID_REQUEST`        | 400         | Request validation failed. | `validationErrors`       | Field validation failed   |
| `INVALID_UUID`           | 400         | Invalid UUID format.       | `field`, `value`         | Malformed UUID            |
| `MISSING_REQUIRED_FIELD` | 400         | Required field is missing. | `field`                  | Null/empty required field |
| `INVALID_ENUM_VALUE`     | 400         | Invalid enum value.        | `field`, `allowedValues` | Unknown enum              |

**Example:**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Request validation failed.",
    "context": {
      "validationErrors": [
        {
          "field": "seatIds",
          "message": "Must not be empty",
          "rejectedValue": []
        },
        {
          "field": "showtimeId",
          "message": "Invalid UUID format",
          "rejectedValue": "not-a-uuid"
        }
      ]
    },
    "requestId": "req-val-001"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

---

## 🌍 Localization Strategy

> **📌 NON-NORMATIVE SECTION**
>
> This section describes the recommended localization approach but is **not binding for backend implementation**.
>
> The backend provides English messages and machine-readable codes. Frontend localization is optional and outside the scope of backend contracts.

### Backend Responsibility
- Returns **machine-readable** `error.code`
- Provides **English** `error.message` as fallback
- Includes **context** data for dynamic messages

### Frontend Responsibility
- Maps `error.code` to localized messages
- Uses `context` fields for dynamic values
- Falls back to `error.message` if translation missing

**Example Translation Map (Frontend):**
```javascript
const errorMessages = {
  en: {
    SEAT_ALREADY_BOOKED: "Some seats are no longer available. {availableSeats} seats remaining.",
    CANCELLATION_TOO_LATE: "Cancellation must be done at least {minHoursRequired} hours before showtime."
  },
  hi: {
    SEAT_ALREADY_BOOKED: "कुछ सीटें अब उपलब्ध नहीं हैं। {availableSeats} सीटें शेष।",
    CANCELLATION_TOO_LATE: "शो से कम से कम {minHoursRequired} घंटे पहले रद्द करना होगा।"
  },
  es: {
    SEAT_ALREADY_BOOKED: "Algunos asientos ya no están disponibles. {availableSeats} asientos restantes.",
    CANCELLATION_TOO_LATE: "La cancelación debe hacerse al menos {minHoursRequired} horas antes de la función."
  }
};

// Frontend usage
function getLocalizedError(error, locale = 'en') {
  const template = errorMessages[locale][error.code] || error.message;
  return template.replace(/\{(\w+)\}/g, (match, key) => error.context[key]);
}
```

---

## 📊 HTTP Status Code Mapping

### Status Code Selection Rules

| Status Range | When to Use                  |
| ------------ | ---------------------------- |
| `400-499`    | Client error (user can fix)  |
| `500-599`    | Server error (backend issue) |

### Specific Status Codes

| Status                      | Category                       | Example Codes                                                      |
| --------------------------- | ------------------------------ | ------------------------------------------------------------------ |
| `400 Bad Request`           | Validation error               | `INVALID_REQUEST`, `SHOWTIME_CLOSED`, `MAX_SEATS_EXCEEDED`         |
| `401 Unauthorized`          | Authentication missing/invalid | `UNAUTHORIZED`, `TOKEN_EXPIRED`                                    |
| `402 Payment Required`      | Payment issue                  | `PAYMENT_DECLINED`, `PAYMENT_FAILED`                               |
| `403 Forbidden`             | Authorization failed           | `FORBIDDEN`, `USER_NOT_VERIFIED`                                   |
| `404 Not Found`             | Resource doesn't exist         | `BOOKING_NOT_FOUND`, `SHOWTIME_NOT_FOUND`                          |
| `409 Conflict`              | Business rule violation        | `SEAT_ALREADY_BOOKED`, `IDEMPOTENCY_CONFLICT`, `ALREADY_CANCELLED` |
| `422 Unprocessable Entity`  | Semantic error                 | `BOOKING_EXPIRED` (rare, prefer 400)                               |
| `500 Internal Server Error` | Unhandled exception            | `INTERNAL_ERROR`, `DATABASE_ERROR`                                 |
| `503 Service Unavailable`   | Temporary outage               | `DOWNSTREAM_TIMEOUT`, `SERVICE_UNAVAILABLE`                        |

---

## 🔍 Context Field Patterns

### Purpose of `context` Field

The `context` object provides **additional data** to help users/frontend understand and resolve the error.

### ⚠️ Security: Strictly Limit Context Contents

**CRITICAL: The `context` field MUST NEVER contain:**
- ❌ **PII (Personal Identifiable Information):** Full names, phone numbers, addresses, SSNs
- ❌ **Stack traces:** Internal code paths, class names, line numbers
- ❌ **Database details:** Table names, column names, SQL queries
- ❌ **Authentication tokens:** JWTs, API keys, session IDs
- ❌ **Payment details:** Full card numbers, CVVs, bank account numbers
- ❌ **Internal IDs exposed to enumeration:** Use UUIDs only, never sequential IDs
- ❌ **System paths:** File paths, server names, internal URLs

**Allowed in `context`:**
- ✅ Public resource identifiers (UUIDs)
- ✅ Business rule parameters (maxSeats, minHours)
- ✅ Counts and aggregates (availableSeats, totalBookings)
- ✅ Sanitized timing information (ISO-8601 timestamps)
- ✅ Enum values (status codes, categories)

**Example - BAD (Security Violation):**
```json
// ❌ NEVER DO THIS
"context": {
  "userEmail": "john@example.com",  // PII leak
  "stackTrace": "at com.ticketledger.BookingService.reserve(BookingService.java:123)",  // Internal details
  "dbQuery": "SELECT * FROM users WHERE id = 123",  // SQL injection risk
  "jwtToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  // Auth leak
}
```

**Example - GOOD (Secure):**
```json
// ✅ Safe context
"context": {
  "bookingId": "019535d9-3df7-79fb-b466-fa907fa17f9e",  // UUID is safe
  "availableSeats": 43,  // Aggregate is safe
  "minHoursRequired": 3,  // Business rule is safe
  "currentStatus": "HELD"  // Enum is safe
}
```

### Common Context Patterns

#### 1. Resource Identifiers
```json
"context": {
  "bookingId": "booking-uuid-123",
  "showtimeId": "showtime-uuid-456"
}
```

#### 2. Rejected/Invalid Values
```json
"context": {
  "rejectedSeatIds": ["seat-uuid-2", "seat-uuid-5"],
  "invalidField": "email",
  "providedValue": "not-an-email"
}
```

#### 3. Business Rule Details
```json
"context": {
  "requestedSeats": 15,
  "maxAllowed": 10,
  "currentStatus": "EXPIRED",
  "requiredStatus": "HELD"
}
```

#### 4. Timing Information
```json
"context": {
  "showtimeStartTime": "2026-01-18T18:00:00Z",
  "currentTime": "2026-01-18T16:30:00Z",
  "minHoursRequired": 3,
  "hoursRemaining": 1.5
}
```

#### 5. Alternative Actions
```json
"context": {
  "availableSeats": 43,
  "alternativeShowtimes": ["showtime-uuid-789"],
  "suggestedAction": "SELECT_DIFFERENT_SEATS"
}
```

---

## 📝 Logging Requirements

### Backend Logging Strategy

**Error Level Mapping:**

| Error Type           | Log Level | Details Logged                      |
| -------------------- | --------- | ----------------------------------- |
| 5xx (System errors)  | `ERROR`   | Full stack trace, request details   |
| 4xx (Client errors)  | `WARN`    | Error code, context, no stack trace |
| Business validations | `INFO`    | Error code only                     |

**Example Log Entry (JSON format):**
```json
{
  "timestamp": "2026-01-18T10:30:00.123Z",
  "level": "WARN",
  "logger": "com.ticketledger.BookingController",
  "message": "Seat already booked",
  "errorCode": "SEAT_ALREADY_BOOKED",
  "requestId": "req-book-001",
  "userId": "user-uuid-123",
  "context": {
    "rejectedSeatIds": ["seat-uuid-2"],
    "showtimeId": "showtime-uuid-456"
  },
  "userAgent": "Mozilla/5.0...",
  "ipAddress": "192.168.1.100"
}
```

### What NOT to Log
- ❌ User passwords (even hashed)
- ❌ Payment card numbers
- ❌ Full `provider_response` (contains sensitive data)
- ❌ JWT tokens

---

## 🛡️ Security Considerations

### 1. Don't Leak Internal Details

```json
// ❌ BAD: Exposes SQL
{
  "error": {
    "code": "DATABASE_ERROR",
    "message": "ERROR: duplicate key value violates unique constraint \"users_email_key\""
  }
}

// ✅ GOOD: Abstracted
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "An account with this email already exists."
  }
}
```

### 2. Don't Expose User Enumeration

```json
// ❌ BAD: Reveals user exists
{
  "error": {
    "code": "WRONG_PASSWORD",
    "message": "Incorrect password for user@example.com"
  }
}

// ✅ GOOD: Generic message
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid email or password."
  }
}
```

### 3. Rate Limit Error Responses

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again later.",
    "context": {
      "retryAfterSeconds": 60,
      "limit": 100,
      "window": "1 hour"
    },
    "requestId": "req-rate-001"
  },
  "meta": {
    "timestamp": "2026-01-18T10:30:00Z"
  }
}
```

---

## 🎯 Frontend Error Handling Guidelines

> **📌 NON-NORMATIVE SECTION**
>
> This section is provided **FOR CONTEXT ONLY** to help frontend developers understand how to consume the backend error API.
>
> **Backend developers:** Do NOT let frontend concerns dictate your error schema design. The backend error format (code, message, context) is the contract. How the frontend consumes it is their responsibility.
>
> **This section is not binding for backend implementation.**

### 1. Error Categorization

```javascript
function handleError(error) {
  const { code, context } = error;
  
  // User-fixable errors (show inline validation)
  if (['INVALID_REQUEST', 'MAX_SEATS_EXCEEDED'].includes(code)) {
    showInlineError(context.validationErrors);
  }
  
  // Retry-able errors (show retry button)
  if (['DOWNSTREAM_TIMEOUT', 'SERVICE_UNAVAILABLE'].includes(code)) {
    showRetryDialog(context.estimatedRecoveryTime);
  }
  
  // Business rule violations (show alternative actions)
  if (code === 'SEAT_ALREADY_BOOKED') {
    showAlternativeSeats(context.availableSeats);
  }
  
  // Critical errors (contact support)
  if (code === 'INTERNAL_ERROR') {
    showSupportDialog(error.requestId);
  }
}
```

### 2. User-Friendly Messages

```javascript
// Map technical errors to user-friendly messages
const userMessages = {
  SEAT_ALREADY_BOOKED: {
    title: "Seats Unavailable",
    message: "Some seats you selected are no longer available. Please select different seats.",
    action: "SELECT_OTHER_SEATS"
  },
  PAYMENT_DECLINED: {
    title: "Payment Failed",
    message: "Your payment was declined. Please check your card details and try again.",
    action: "RETRY_PAYMENT"
  },
  CANCELLATION_TOO_LATE: {
    title: "Cancellation Not Allowed",
    message: "Bookings can only be cancelled at least 3 hours before showtime.",
    action: "CONTACT_SUPPORT"
  }
};
```

### 3. Analytics Tracking

```javascript
// Track errors for monitoring
function trackError(error) {
  analytics.track('API_Error', {
    errorCode: error.code,
    httpStatus: error.httpStatus,
    endpoint: error.endpoint,
    requestId: error.requestId,
    userId: currentUser.id,
    timestamp: new Date()
  });
}
```

---

## ✅ Error Handling Checklist

- [x] Standard error envelope defined
- [x] Machine-readable error codes cataloged
- [x] Context field patterns documented
- [x] HTTP status code mapping established
- [x] Localization strategy defined
- [x] Logging requirements specified
- [x] Security considerations outlined
- [x] Frontend integration guidelines provided

---

## 🔍 Cross-Reference

- **API Contracts:** See `05_api_contracts.md`
- **State Machines:** See `02_lifecycle_states.md`
- **Sequence Flows:** See `03_sequence_flows.md`
- **Database Schema:** See `04_database_schema.md`