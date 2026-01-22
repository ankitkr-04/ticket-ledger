# Decision 007: Native API Versioning (Spring Boot 4)

## 📋 Purpose

This document explains the **API versioning strategy** for TicketLedger and how we leverage Spring Boot 4's native versioning support to manage API evolution without manual path prefixing.

### ✅ This file contains:
- Context: Why we need API versioning
- Decision: Spring Boot 4 native path-segment versioning
- Configuration: YAML setup and controller annotations
- Migration strategy: Removing manual `/v1` prefixes
- Trade-offs: Path vs header vs parameter versioning

### ❌ This file does NOT contain:
- Deprecation policies (covered in API lifecycle docs)
- Breaking change definitions (see API evolution guidelines)
- Client migration guides (see API consumer documentation)

---

## 🎯 Context

### The Problem

TicketLedger is building a **stable, long-lived API**. As business requirements evolve, we will inevitably need to introduce breaking changes:
- Changing the booking flow structure
- Modifying response schemas
- Altering business rules

**Current Approach (Manual Prefixing):**
```java
@RequestMapping("/api/v1/bookings")
public class BookingController { ... }
```

**Issues with Manual Versioning:**
1. **Error-prone:** Developers must remember to add `/v1` to every route
2. **Difficult to evolve:** Supporting multiple concurrent versions (e.g., `v1` for mobile, `v2` for web) requires route duplication
3. **No framework support:** Version validation, routing, and documentation are manual
4. **Inconsistent:** Easy to accidentally create unversioned endpoints

### Requirements

| Requirement                  | Priority | Rationale                                                    |
| ---------------------------- | -------- | ------------------------------------------------------------ |
| **Explicit versioning**      | Critical | Clients must clearly declare their API contract version      |
| **Parallel version support** | High     | Old mobile apps (v1) must work while new web apps use v2     |
| **Clean URLs**               | High     | `/api/v1/bookings` is intuitive and cacheable                |
| **Framework-driven**         | High     | Reduce manual errors, enable Swagger/OpenAPI auto-generation |
| **Minimal migration effort** | Medium   | Retrofit existing controllers without full rewrites          |

---

## 📌 Decision

We will adopt **Spring Boot 4 Native Path Versioning** (via Spring Framework 7).

### The Strategy

| Aspect               | Choice                               | Rationale                                                         |
| -------------------- | ------------------------------------ | ----------------------------------------------------------------- |
| **Mechanism**        | Path Segment (`/api/v1/...`)         | Most explicit, CDN-friendly, industry standard                    |
| **Version Format**   | `v{Major}` (e.g., `v1`, `v2`)        | Clean URLs; avoid `1.0` notation to reduce verbosity              |
| **Breaking Changes** | Increment Major (e.g., release `v2`) | Clear signal to clients that migration is required                |
| **Default Version**  | `v1`                                 | Fallback for clients that omit version (if strictness is relaxed) |
| **Enforcement**      | Required by default                  | Forces clients to be version-aware                                |

### Why Path Versioning Over Alternatives?

#### ✅ Path Segment: `/api/v1/bookings` (CHOSEN)
- **Explicit:** Version is immediately visible in the URL
- **Cacheable:** CDNs/browsers treat `/v1/movies` and `/v2/movies` as distinct cache keys
- **Tooling:** Swagger generates separate specs per version
- **Industry Standard:** AWS, Stripe, GitHub all use path versioning

#### ❌ Header Versioning: `Accept: application/vnd.ticketledger.v1+json`
- **Pros:** Clean URLs, no URL pollution
- **Cons:** Not cache-friendly, hard to test in browsers, poor discoverability

#### ❌ Query Parameter: `/api/bookings?version=1`
- **Pros:** Easy to add to existing endpoints
- **Cons:** Cache busting issues, easy to omit accidentally, looks unprofessional

---

## 🛠️ Implementation Specification

### Configuration (`application.yaml`)

We configure Spring MVC to look for the version at **index 1** (0-based) of the URL path:

```yaml
spring:
  mvc:
    apiversion:
      use:
        path-segment: 1   # Index 1: /api (0) /v1 (1) /bookings (2)
      required: true      # Enforce version in all requests
      default: "v1"       # Fallback if strictness is relaxed
```

**Path Structure Breakdown:**
```
URL: /api/v1/bookings/123
     └─0─┘ └1┘ └──────2─────┘
     base  version  resource
```

### Controller Contract

**Before (Manual Prefixing):**
```java
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    
    @PostMapping
    public BookingResponse create(@RequestBody BookingRequest request) {
        // Business logic
    }
}
```

**After (Native Versioning):**
```java
@RestController
@RequestMapping(path = "/api/bookings", version = "v1")
public class BookingController {
    
    @PostMapping
    public BookingResponse create(@RequestBody BookingRequest request) {
        // Business logic (unchanged)
    }
}
```

**Key Changes:**
- Remove `/v1` from `@RequestMapping` path
- Add `version = "v1"` attribute
- Framework automatically resolves final route to `/api/v1/bookings`

### Route Constants Refactoring

**Before:**
```java
public class RouteConstants {
    public static final String API_V1_BASE = "/api/v1";
    public static final String BOOKINGS_PATH = API_V1_BASE + "/bookings";
    public static final String ADMIN_BOOKING_PATH = API_V1_BASE + "/admin/bookings";
}
```

**After:**
```java
public class RouteConstants {
    public static final String API_BASE = "/api";
    public static final String BOOKINGS_PATH = API_BASE + "/bookings";
    public static final String ADMIN_BOOKING_PATH = API_BASE + "/admin/bookings";
    
    // Version is declared in controller, not in path constants
    public static final String VERSION_1 = "v1";
}
```

---

## 🔄 Migration Plan

### Phase 1: Configuration Setup
1. Add API versioning configuration to `application.yaml`
2. Verify startup logs confirm versioning is active

### Phase 2: Refactor Constants
1. Update `RouteConstants` to remove `/v1` prefixes
2. Update any hardcoded paths in test utilities
3. Run existing integration tests (should fail initially)

### Phase 3: Update Controllers
1. Add `version = "v1"` to all `@RequestMapping` annotations:
   - `BookingController`
   - `MovieController`
   - `ShowtimeController`
   - `AdminBookingController`
   - `AuthController`
   - `HealthController` (may remain unversioned)

### Phase 4: Update Tests
1. Ensure integration tests use resolved paths, not hardcoded `/v1` strings
2. Add tests for version enforcement (e.g., reject requests without version if `required: true`)
3. Validate Swagger UI displays `/v1` endpoints correctly

---

## ⚖️ Trade-offs

### ✅ Positive Consequences

| Benefit                        | Impact                                                                          |
| ------------------------------ | ------------------------------------------------------------------------------- |
| **Easy V2 Introduction**       | Create `BookingControllerV2` alongside V1; framework routes based on `version`  |
| **Decoupled Versioning Logic** | Business logic paths (`/bookings`) are separate from versioning concerns (`v1`) |
| **Framework Validation**       | Spring enforces version presence; prevents accidental unversioned endpoints     |
| **Swagger Integration**        | OpenAPI automatically generates separate specs per version                      |
| **Client Clarity**             | Mobile app can pin `v1`, web app can adopt `v2` independently                   |

### ⚠️ Negative Consequences

| Challenge                    | Mitigation                                                                   |
| ---------------------------- | ---------------------------------------------------------------------------- |
| **Test Updates Required**    | Update all integration tests to use resolved paths (one-time migration cost) |
| **URL Length Increase**      | `/v1` adds 3 characters; acceptable trade-off for explicitness               |
| **Version in Every Request** | Alternative (header versioning) has worse discoverability/caching            |
| **Learning Curve**           | Team must understand framework resolves final URL (document in onboarding)   |

---

## 📚 Related Decisions

- **Authentication Strategy** (`01_authentication_strategy.md`): JWT tokens remain version-agnostic
- **API Contracts** (`05_api_contracts.md`): Request/response schemas are versioned per endpoint
- **Admin API** (`08_admin_api_contracts.md`): Admin endpoints follow same versioning rules

---

## 🔮 Future Considerations

### When to Introduce V2?
- **Schema Changes:** Adding required fields to request bodies
- **Flow Changes:** Modifying booking state transitions
- **Security Changes:** Changing authentication mechanisms

### Deprecation Policy
- V1 endpoints remain active for **6 months** after V2 release
- Clients receive `Sunset` HTTP header with deprecation date
- Admin dashboard tracks V1 usage metrics to plan sunsetting

### Version Discovery
- `GET /api` returns available versions and their capabilities
- Swagger UI dropdown allows browsing V1 and V2 specs side-by-side
