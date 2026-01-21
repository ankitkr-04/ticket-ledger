# TicketLedger Documentation

## 📚 Reading Order

This documentation follows a **Design-First narrative**. Read in numerical order for the best understanding:

1. **[01_problem_statement.md](01_problem_statement.md)** - The "Why"
   - Business problem and requirements
   - Sets the context for all design decisions

2. **[02_lifecycle_states.md](02_lifecycle_states.md)** - The "What"
   - Finite state machines for all entities
   - Allowed and denied transitions
   - **Critical:** Read this before understanding flows

3. **[03_sequence_flows.md](03_sequence_flows.md)** - The "How"
   - End-to-end transaction flows
   - **Critical:** Flow H addresses concurrent seat reservation
   - Payment retry handling (Flow A.1)

4. **[04_database_schema.md](04_database_schema.md)** - The "Storage"
   - Complete table definitions
   - **Key insight:** `seats.status` is a materialized lock state
   - `bookings.status` is the financial source of truth

5. **[05_api_contracts.md](05_api_contracts.md)** - The "Interface"
   - REST API specifications
   - **New:** Payment retry endpoint (`POST /bookings/{id}/payment-intents`)
   - Request/response schemas

6. **[06_error_handling.md](06_error_handling.md)** - The "Edge Cases"
   - Error response format
   - Machine-readable error codes
   - **Security:** Strict rules for `error.context` field (no PII, no stack traces)

---

## 🧠 Design Decisions

Deep dives into architectural choices and trade-offs:

- **[decisions/01_authentication_strategy.md](decisions/01_authentication_strategy.md)** - Auth Architecture
  - Why stateless authentication?
  - JWT for access + Opaque token for refresh
  - Token rotation security analysis
  - User registration and logout implementation
  - **Key insight:** 4% DB overhead for revocation capability

- **[decisions/02_idempotency_storage.md](decisions/02_idempotency_storage.md)** - Idempotency Strategy
  - Single generic table vs entity-specific tables
  - PostgreSQL row-level locking for concurrency
  - JSONB for flexible response storage
  - Transaction rollback safety
  - **Key insight:** Built-in locking eliminates distributed consensus

- **[decisions/003_concurrency_model.md](decisions/003_concurrency_model.md)** - Virtual Threads
  - Java 21+ Virtual Threads for I/O-bound workload
  - Thread-per-request scalability (millions of threads)
  - Connection pool as hard limit (HikariCP)
  - Pinning risks and mitigation strategies
  - **Key insight:** 85% time spent waiting on I/O

- **[decisions/004_event_driven_notifications.md](decisions/004_event_driven_notifications.md)** - Async Events
  - Spring `@TransactionalEventListener` pattern
  - `AFTER_COMMIT` phase guarantees
  - At-Most-Once delivery for MVP (acceptable trade-off)
  - Async execution with Virtual Threads
  - **Key insight:** Email failure doesn't rollback booking

- **[decisions/005_observability_strategy.md](decisions/005_observability_strategy.md)** - Logging & Tracing
  - MDC (Mapped Diagnostic Context) for correlation
  - Automatic MDC propagation to Virtual Threads
  - Structured logging (JSON in prod, console in dev)
  - End-to-end request tracing across threads
  - **Key insight:** Same `requestId` across async boundaries

---

## 🎯 Quick Reference

### Architecture Deep Dives
- **Concurrency:** Read Flow H in [03_sequence_flows.md](architecture/03_sequence_flows.md#flow-h-concurrent-seat-reservation-the-race-condition)
- **Virtual Threads:** See [decisions/003_concurrency_model.md](decisions/003_concurrency_model.md)
- **Async Events:** See [decisions/004_event_driven_notifications.md](decisions/004_event_driven_notifications.md)
- **Observability:** See [decisions/005_observability_strategy.md](decisions/005_observability_strategy.md)
- **State Machines:** See [02_lifecycle_states.md](architecture/02_lifecycle_states.md)
- **Database Design:** See "Architecture Note" in [04_database_schema.md](architecture/04_database_schema.md#seat_status)
- **Authentication:** See [decisions/01_authentication_strategy.md](decisions/01_authentication_strategy.md)
- **Idempotency:** See [decisions/02_idempotency_storage.md](decisions/02_idempotency_storage.md)

### For Implementation
- **API Endpoints:** [05_api_contracts.md](architecture/05_api_contracts.md)
- **Database Schema:** [04_database_schema.md](architecture/04_database_schema.md)
- **Error Codes:** [06_error_handling.md](architecture/06_error_handling.md)
- **Auth Strategy:** [decisions/01_authentication_strategy.md](decisions/01_authentication_strategy.md)
- **Idempotency:** [decisions/02_idempotency_storage.md](decisions/02_idempotency_storage.md)

### For Code Review
- **Transaction Boundaries:** Each flow in [03_sequence_flows.md](architecture/03_sequence_flows.md) documents ACID boundaries
- **Lock Acquisition Order:** Flow H demonstrates deadlock prevention
- **Source of Truth:** [02_lifecycle_states.md](architecture/02_lifecycle_states.md#-architecture-seat-status-as-locking-mechanism)
- **Security Patterns:** Token rotation in [decisions/01_authentication_strategy.md](decisions/01_authentication_strategy.md)
- **Idempotency Locking:** Row-level locks in [decisions/02_idempotency_storage.md](decisions/02_idempotency_storage.md)

---

## 🏗️ Architecture Principles

1. **Bookings are the Financial Ledger** - Never delete, only state transition
2. **Seats are the Locking Mechanism** - Optimized for concurrency
3. **Transactions are Atomic** - Seat + Booking transitions in same TX
4. **Locks are Sorted** - Prevents deadlocks via consistent ordering
5. **Errors are Machine-Readable** - Frontend-agnostic error codes
6. **Authentication is Hybrid** - JWT (stateless) for speed + Opaque tokens (stateful) for security
7. **Virtual Threads for I/O** - Scalable thread-per-request model for blocking operations
8. **Events After Commit** - Side effects decouple from financial transactions
9. **Observability First** - Correlation IDs across threads for end-to-end tracing

---

## 🤝 Contributing

When updating documentation:
- ✅ Maintain the numbered sequence
- ✅ Update cross-references if file structure changes
- ✅ Keep "Design First" narrative flow

---

## 📧 Questions?

This documentation is living and evolving. If something is unclear, it's a documentation bug, not a reader bug. Please raise an issue.
