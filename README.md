# GigLedger
A decentralized, tamper-evident ledger and automated redressal system for gig workers.

## Problem Statement
Gig workers in the delivery and ride-hailing sectors (e.g., Swiggy, Zomato, Uber) frequently face "algorithmic wage theft"—where platform payouts quietly drop below promised baselines due to opaque algorithmic changes. Workers lack the tools to detect micro-deductions, prove their historical earnings reliably, and draft professional legal grievances when they are shortchanged. 

GigLedger solves this by automatically ingesting earnings screenshots, tracking rate drops via anomaly detection, securing the logs with a cryptographic hash-chain, and auto-drafting PDF grievance reports that can be sent directly to platform officers.

## Architecture
- **React Frontend**: Multilingual SPA interface with audio recording capabilities.
- **Spring Boot Backend**: Core accounting, PostgreSQL management, Hash-Chain integrity block writing, JWT Auth, PDF generation, and Resend API dispatch.
- **FastAPI ML Service**: Python microservice handling AI offloading.
- **External AI Integrations**:
  - **Groq LLM**: Fast intent parsing (Voice commands) and news summarization.
  - **Sarvam AI**: Indic language Text-to-Speech (TTS) and Speech-to-Text (STT) for Tamil voice features.
  - **NewsAPI / PIB**: Public policy and union news ingestion.

## Feature Modules
- **Core Ledger**: Logs daily tasks (promised pay, distance) and actual payouts. 
- **OCR Engine**: Extracts payout data directly from platform screenshots.
- **Anomaly Detection**: Evaluates rolling 30-day payout data to detect systemic rate shocks (e.g., a 20% cut in per-km pay).
- **Hash-Chain Integrity**: Cryptographically hashes every payout log sequentially. Any manual database tampering breaks the chain, providing verifiable proof of data integrity.
- **PDF Redressal Export**: Generates tamper-evident Earnings Reports combining the data logs and the cryptographic status shield.
- **Auto-Draft Complaints**: When anomalies are flagged, the system maps the platform to the correct legal contact (e.g., `grievances@swiggy.in`) and drafts a verified complaint.
- **Policy Pulse**: An AI-curated news feed of relevant e-Shram policies and platform union updates, summarized and categorized by urgency.
- **GigVoice**: A multilingual microphone assistant allowing workers to log earnings via native speech (e.g., Tamil) hands-free.
- **Google OAuth**: Secure, seamless sign-in alternative to traditional passwords.
- **Email Notifications**: Alert dispatcher for pay discrepancies and draft availability, complete with user preference toggles.
- **Fuel-Cost Fairness**: Cross-references live state-level petrol prices to flag trips where promised pay fails to cover estimated fuel expenditure.

## Tech Stack
| Layer | Technology |
|---|---|
| **Frontend** | React, Vite, Vanilla CSS |
| **Backend API** | Java 17, Spring Boot, Spring Security (JWT) |
| **ML Microservice** | Python, FastAPI, Uvicorn, Pandas |
| **Database** | PostgreSQL |
| **AI Models** | Groq (Llama3-8b), Sarvam AI (Indic STT/TTS) |
| **PDF Generation** | OpenPDF (Java) |

## Honest Limitations
1. **SMS Notifications**: SMS dispatch was scoped out of this prototype due to India's strict DLT (Distributed Ledger Technology) business registration requirements for sending automated text messages.
2. **Email Restrictions**: The application currently uses Resend's free tier, which restricts automated emails strictly to verified developer addresses. Adding a custom domain is required for production rollout to all users.
3. **OCR Constraints**: OCR accuracy fluctuates heavily depending on the specific UI format of the delivery app screenshot (Swiggy vs. Zomato).
4. **Fuel Efficiency**: Fuel consumption calculations are based on user-provided average assumptions (e.g., 45 km/l) rather than live telemetry, and are used as estimates.

## Verification & Proof
The system has been rigorously tested through automated pipelines:
- **Hash-Chain Tamper Test**: Direct SQL manipulation of database records successfully triggered the `INTEGRITY COMPROMISED` alert in the PDF engine.
- **Rate Shock Anomaly**: A controlled 25% drop in simulated payouts successfully triggered the `DISCREPANCY_FLAG` module.
- **Multilingual Voice AI**: Round-trip tests passing generated Tamil Text-to-Speech audio back into the Speech-to-Text engine achieved >91% accuracy scores.
