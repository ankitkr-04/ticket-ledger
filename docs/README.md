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
  - Performance vs security trade-offs
  - **Key insight:** 4% DB overhead for revocation capability

- **[decisions/02_idempotency_storage.md](decisions/02_idempotency_storage.md)** - Idempotency Strategy
  - Single generic table vs entity-specific tables
  - PostgreSQL row-level locking for concurrency
  - JSONB for flexible response storage
  - Transaction rollback safety
  - **Key insight:** Built-in locking eliminates distributed consensus

---

## 🎯 Quick Reference

### Architecture Deep Dives
- **Concurrency:** Read Flow H in [03_sequence_flows.md](architecture/03_sequence_flows.md#flow-h-concurrent-seat-reservation-the-race-condition)
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

## 📝 Recent Updates

### January 19, 2026
1. ✅ Added comprehensive authentication strategy documentation
2. ✅ Documented JWT + Opaque token hybrid approach
3. ✅ Explained token rotation security with attack scenarios
4. ✅ Analyzed DB performance trade-offs (4% overhead)
5. ✅ Added Mermaid diagrams for token lifecycle and attack timelines
6. ✅ Created idempotency storage strategy document
7. ✅ Documented PostgreSQL row-level locking for concurrency
8. ✅ Added failure invariant (transaction rollback safety)
9. ✅ Updated database schema with idempotency_keys table

### January 18, 2026
1. ✅ Added payment retry endpoint for handling declined cards
2. ✅ Clarified `seats.status` as materialized lock state (not source of truth)
3. ✅ Added Flow H: Concurrent Seat Reservation (the race condition diagram)
4. ✅ Added security constraints for `error.context` field
5. ✅ Marked frontend sections as non-normative
6. ✅ Restructured docs to linear numbered sequence

---

## 🏗️ Architecture Principles

1. **Bookings are the Financial Ledger** - Never delete, only state transition
2. **Seats are the Locking Mechanism** - Optimized for concurrency
3. **Transactions are Atomic** - Seat + Booking transitions in same TX
4. **Locks are Sorted** - Prevents deadlocks via consistent ordering
5. **Errors are Machine-Readable** - Frontend-agnostic error codes
6. **Authentication is Hybrid** - JWT (stateless) for speed + Opaque tokens (stateful) for security

---

## 🤝 Contributing

When updating documentation:
- ✅ Maintain the numbered sequence
- ✅ Update cross-references if file structure changes
- ✅ Add to "Recent Updates" section above
- ✅ Keep "Design First" narrative flow

---

## 📧 Questions?

This documentation is living and evolving. If something is unclear, it's a documentation bug, not a reader bug. Please raise an issue.
