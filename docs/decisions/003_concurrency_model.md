# Decision 003: Virtual Threads Concurrency Model

## 📋 Purpose

This document explains the **concurrency model** for TicketLedger and why we chose Java 21 Virtual Threads over traditional platform threads or reactive programming models.

### ✅ This file contains:
- Context: Why concurrency matters for I/O-bound workloads
- Decision: Java 21 Virtual Threads configuration
- Rationale: Thread-per-request scalability analysis
- Constraints: Connection pooling and pinning risks
- Trade-offs: Virtual threads vs Reactive/WebFlux

### ❌ This file does NOT contain:
- Implementation details (see Spring configuration)
- Database optimization strategies (see separate docs)
- Load testing results (see performance testing docs)

---

## 1. Context

TicketLedger is a **high-concurrency, I/O-bound** application. The core workload involves:

- **Waiting for database row locks** (`PESSIMISTIC_WRITE` during seat booking)
- **Waiting for external Payment Gateway webhooks** (Stripe/PayPal API calls)
- **Waiting for SMTP email acknowledgments** (confirmation emails)
- **Waiting for HTTP responses** (potential future external APIs)

### The I/O Bound Problem

```mermaid
gantt
    title Typical Request Timeline (Booking Flow)
    dateFormat X
    axisFormat %Lms
    
    Validate Request :a1, 0, 5
    Acquire DB Lock (BLOCKING) :crit, a2, 5, 155
    Update Seat Status :a3, 155, 175
    Create Booking Record :a4, 175, 185
    Call Payment Gateway (BLOCKING) :crit, a5, 185, 265
    Return Response :a6, 265, 270
```

**Key Observation:** The thread is **blocked 85% of the time** (waiting on I/O), consuming platform thread resources while doing nothing.

---

## 2. Problem

**Traditional "Platform Threads"** (1 Java Thread = 1 OS Thread) are expensive:

- **Memory Overhead:** Each platform thread allocates ~1MB of stack memory
- **Context Switching:** OS-level context switching is expensive at scale
- **Thread Pool Exhaustion:** Default Spring Boot thread pool (200 threads) can only handle 200 concurrent blocking operations
- **Low CPU Utilization:** Threads sit idle waiting for I/O, wasting resources

### Example: High-Load Scenario

```
Load: 1000 concurrent booking requests
Platform Threads Available: 200 (default Tomcat pool)

Result:
- 200 requests processed simultaneously
- 800 requests queued (latency increases linearly)
- CPU usage: 15% (threads mostly waiting)
- Throughput: Limited by thread pool, not CPU/DB
```

**The Paradox:** We cannot scale horizontally by adding more threads without exhausting memory, yet CPU sits idle.

---

## 3. Decision

We will enable **Java 21 Virtual Threads** in Spring Boot.

### Configuration

**`application.yaml`:**
```yaml
spring:
  threads:
    virtual:
      enabled: true  # Enable Virtual Threads for web requests
```

**Effect:**
- All HTTP request handlers run on virtual threads
- All `@Async` methods use virtual threads (if configured)
- Database operations automatically benefit from virtual thread parking

---

## 4. Rationale

### Why Virtual Threads?

#### 4.1 Efficiency: Lightweight Threads

- **Virtual Threads are cheap:** ~1KB of memory per thread (1000x smaller than platform threads)
- **Massive concurrency:** Can spawn millions of virtual threads without memory exhaustion
- **Example:** 1 million virtual threads ≈ 1GB RAM vs 1TB RAM for platform threads

#### 4.2 Simplicity: Thread-per-Request Model

- **No code changes:** Existing blocking JDBC/HTTP code works unchanged
- **No Reactive complexity:** Avoid learning curve of Project Reactor/WebFlux
- **Easier debugging:** Stack traces are linear, not callback chains

**Comparison:**
```java
// ❌ Platform Threads: Limited by pool size
@GetMapping("/booking/{id}")
public BookingResponse getBooking(@PathVariable UUID id) {
    return bookingService.findById(id); // Blocks a precious platform thread
}

// ✅ Virtual Threads: Same code, scales to millions
@GetMapping("/booking/{id}")
public BookingResponse getBooking(@PathVariable UUID id) {
    return bookingService.findById(id); // Blocks a cheap virtual thread
}
```

#### 4.3 Parking: Efficient I/O Waiting

When a virtual thread blocks (e.g., waiting for DB lock), the JVM **unmounts** it from the carrier thread:

```
┌──────────────────────────────────────────┐
│ Carrier Thread (OS Thread)               │
├──────────────────────────────────────────┤
│ VThread-1 → Blocked on DB Lock           │
│   ↓ JVM detects blocking I/O             │
│   ↓ Unmounts VThread-1                   │
│ VThread-2 → Executes while VT-1 waits    │
│   ↓ DB returns result for VT-1           │
│   ↓ JVM remounts VThread-1               │
│ VThread-1 → Continues execution          │
└──────────────────────────────────────────┘
```

**Result:** One OS thread can serve thousands of virtual threads efficiently.

---

## 5. Constraints & Risks

### 5.1 Pinning: Avoid `synchronized` Blocks

**Problem:** Virtual threads can get **pinned** to carrier threads inside `synchronized` blocks or native method calls, blocking the OS thread.

**Solution:**
```java
// ❌ BAD: Pinning issue
private synchronized void updateBooking(Booking booking) {
    // Virtual thread is pinned to carrier thread
    bookingRepository.save(booking); // Blocking call wastes OS thread
}

// ✅ GOOD: Use ReentrantLock
private final ReentrantLock lock = new ReentrantLock();

private void updateBooking(Booking booking) {
    lock.lock();
    try {
        bookingRepository.save(booking); // Virtual thread can park
    } finally {
        lock.unlock();
    }
}
```

**Mitigation Strategy:**
- Code review: Flag all `synchronized` keywords
- Use `ReentrantLock` or `StampedLock` for explicit locking
- Enable JVM flag: `-Djdk.tracePinnedThreads=full` (development only) to detect pinning

### 5.2 Connection Pool: The Hard Limit

**Critical Understanding:** Virtual threads do NOT increase database throughput beyond the connection pool size.

**HikariCP Configuration:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Hard limit: 20 concurrent DB connections
```

**Scenario:**
```
- 10,000 virtual threads spawned
- Only 20 DB connections available
- 20 threads execute queries simultaneously
- 9,980 threads park efficiently waiting for a connection
```

**Key Point:** Virtual threads make **waiting efficient**, but they cannot magically create more DB connections. The database is still the bottleneck.

**Solution:**
- Scale DB connection pool based on DB server capacity (not thread count)
- Monitor connection pool utilization: `hikaricp_connections_active`
- Consider read replicas for read-heavy queries

### 5.3 Failure Modes: Unbounded Thread Creation

**Risk:** What if we spawn 100,000 threads due to a traffic spike?

**Protections:**
1. **Rate Limiting:** Enforce request rate limits (e.g., 100 req/sec per IP)
2. **Connection Pool:** Acts as natural backpressure (threads queue for DB connections)
3. **Load Balancer Limits:** Cloud provider limits (e.g., AWS ALB max connections)
4. **Circuit Breakers:** Fail fast if downstream services are overloaded

**Monitoring:**
```java
// Metrics to track
- jvm.threads.live (should grow with load but not infinitely)
- hikaricp.connections.pending (backpressure indicator)
- http.server.requests.active (concurrent requests)
```

---

## 6. Trade-offs

### Virtual Threads vs Reactive/WebFlux

| Aspect               | Virtual Threads          | Reactive (WebFlux)       |
| -------------------- | ------------------------ | ------------------------ |
| **Learning Curve**   | ✅ Low (familiar model)   | ❌ High (reactive chains) |
| **Code Readability** | ✅ Sequential/imperative  | ❌ Callback hell          |
| **JDBC Support**     | ✅ Fully compatible       | ⚠️ Requires R2DBC         |
| **Debugging**        | ✅ Standard stack traces  | ❌ Complex async stacks   |
| **Library Support**  | ✅ All blocking libraries | ⚠️ Needs reactive libs    |
| **Performance**      | ✅ Excellent for I/O      | ✅ Excellent for I/O      |
| **Maturity**         | ⚠️ New (Java 21+)         | ✅ Mature (Spring 5+)     |

**Decision:** Virtual Threads win for **developer productivity** and **maintainability** in this MVP phase.

---

## 7. Alternatives Considered

### 7.1 Platform Threads + Larger Pool

**Approach:** Increase Tomcat thread pool to 1000 threads

**Rejected Because:**
- 1000 threads × 1MB = 1GB memory just for thread stacks
- Context switching overhead increases with thread count
- Does not solve the core problem (blocking I/O)

### 7.2 Project Reactor (WebFlux)

**Approach:** Rewrite entire application using reactive programming

**Rejected Because:**
- Steep learning curve for team
- R2DBC has limited driver support vs JDBC
- Complex debugging and error handling
- Overkill for MVP timeline

---

## 8. Implementation Checklist

- [x] Enable `spring.threads.virtual.enabled=true`
- [ ] Audit codebase for `synchronized` blocks → Replace with `ReentrantLock`
- [ ] Configure JVM flag `-Djdk.tracePinnedThreads=full` in dev/staging
- [ ] Monitor `jvm.threads.live` and `hikaricp.connections.pending` metrics
- [ ] Document pinning risks in team wiki
- [ ] Load test with 10,000 concurrent requests

---

## 9. References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot 3.2 Virtual Threads Support](https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual-threads)
- [HikariCP Best Practices](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)

