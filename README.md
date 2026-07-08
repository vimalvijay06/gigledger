# GigLedger 📊

> **An Independent Pay Verification Platform for Gig and Delivery Workers**

## 🚨 The Problem

Gig and delivery workers (e.g., Swiggy, Zomato, Blinkit riders) are paid on a per-task basis. When a worker accepts an order, the platform application shows them a **promised fare** (e.g., "₹50 for this delivery"). 

However, the **actual amount** credited to them after completing the task often differs. This happens due to unexplained deductions, hidden adjustments, or platform-side errors that are rarely justified to the worker. Currently, gig workers face a massive systemic disadvantage:
1. They have no independent way to record what was promised versus what they actually received.
2. They cannot track this financial gap over time.
3. They have no way to prove whether a single discrepancy is a one-off mistake or part of a genuine, sustained pattern of underpayment.

The platform's own application acts as the sole record of truth. **There is no independent, worker-owned ledger.**

## 💡 The Solution

**GigLedger** is a secure, mobile-first web application designed to act as a worker's personal, tamper-evident earnings ledger. It empowers delivery partners to independently log, track, and verify their earnings against platform promises.

### Core Features
- **Independent Task Logging:** Workers can securely log the promised fare and distance the moment they accept a task.
- **Payout Verification:** Once paid, workers log the actual amount received. GigLedger automatically calculates and tracks the discrepancy gap.
- **Data Isolation & Security:** Every worker's ledger is completely private. Robust backend security ensures users can only access their own financial data.
- **Future Roadmap (Phase 2 & 3):**
  - **OCR Integration:** Automatically extract promised fares from screenshots to eliminate manual entry.
  - **Statistical Analysis:** Algorithmically flag sustained underpayment patterns across multiple tasks.
  - **Tamper-Evident Logs:** Immutable discrepancy flagging for indisputable proof of underpayment.

## 🛠️ Technology Stack

GigLedger is built with a modern, scalable, and secure architecture:

**Frontend**
- React.js (via Vite)
- Mobile-first, responsive design tailored for use on the road
- Axios for secure API communication with JWT interceptors

**Backend**
- Spring Boot 3.x (Java)
- Spring Security (Stateless JWT Authentication)
- Global exception handling for secure, clean JSON API responses
- Hibernate / Spring Data JPA

**Database**
- PostgreSQL (Relational data integrity for financial records)

## 🔒 Security Posture

- **Stateless Authentication:** Fully secured via JSON Web Tokens (JWT). No session state is maintained on the server.
- **Strict Data Ownership:** Service-level authorization ensures isolated data access (403 Forbidden on cross-user data attempts).
- **Hardened Validation:** Strict DTO validation ensures data integrity at the controller boundary (e.g., preventing negative payouts or impossibly large fares).
- **Environment Driven:** Zero hardcoded secrets. Database credentials and cryptographic keys are injected via environment variables.

---

*GigLedger — Because every worker deserves a single source of truth for their hard-earned money.*
