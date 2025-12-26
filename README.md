# CloudQueue# CloudQueue – Serverless Asynchronous Job Processing System

CloudQueue is a **serverless, event-driven job processing system** built on AWS.  
It accepts tasks via an HTTP API, processes them asynchronously using a queue-based architecture, and persists job state throughout the lifecycle.

The project focuses on **correct distributed-system design** rather than application features, demonstrating patterns used in real production systems such as decoupling, idempotency, retries, and failure isolation.

---

## High-Level Architecture

<!-- TODO: Add an architecture diagram image here.
     Suggested file path: diagrams/architecture.png
     Example usage:
     ![CloudQueue architecture](diagrams/architecture.png)
-->

```
Client / Browser
        ↓
API Gateway (HTTP API)
        ↓
Lambda (Java – API)
        ↓
Amazon SQS
        ↓
Lambda (Python – Worker)
        ↓
Amazon DynamoDB
```

---

## System Overview

CloudQueue is intentionally split into **two independent components**:

### 1) API Layer (Job Producer)
- Exposes a REST endpoint to submit jobs
- Validates input
- Persists initial job metadata
- Enqueues jobs for asynchronous processing
- Implemented in **Java 21** to reflect strongly-typed backend services

### 2) Worker Layer (Job Consumer)
- Consumes jobs from SQS
- Claims jobs idempotently
- Executes background processing
- Updates job state and results
- Implemented in **Python 3.11** for rapid iteration and clarity

This separation mirrors how real systems isolate **user-facing traffic** from **background execution**.

---

## Core Features

### Job Submission
- `POST /jobs`
- Accepts a job type and payload
- Returns immediately with a `jobId`
- Does not block on execution

### Asynchronous Processing
- Jobs are processed independently of the request lifecycle
- API remains responsive under load
- Failures do not impact request handling

### Job Lifecycle Tracking
Jobs transition through explicit states:
- `QUEUED`
- `IN_PROGRESS`
- `SUCCEEDED`
- `FAILED`

All state is persisted in DynamoDB.

### Idempotent Worker Execution
- Conditional updates ensure a job is claimed only once
- Duplicate deliveries or retries do not cause duplicate work
- Safe reprocessing on transient failures

### Failure Handling
- Automatic retries via SQS
- Dead-letter queue configured for poison messages
- Explicit failure state persisted in DynamoDB

---

## Example Job Type

The system supports multiple job types.

A simple example used for demonstration:

**Text Transformation Job**
- Input: `{ "text": "hello cloudqueue" }`
- Output:
  - original length
  - uppercase transformation
  - word count

The **job logic itself is intentionally trivial** — the architectural patterns are the focus.

---

## Public Demo Frontend (Planned)

<!-- TODO: Add a screenshot of the demo UI here once implemented.
     Suggested file path: frontend/demo-screenshot.png
     Example usage:
     ![CloudQueue demo UI](frontend/demo-screenshot.png)
-->

A minimal frontend will be added to demonstrate end-to-end usage:

### Scope
- Single-page static UI
- Hosted on **Amazon S3 + CloudFront**
- No authentication (public demo)
- No backend logic in the frontend

### Capabilities
- Submit a job via the API
- Display the returned `jobId`
- Poll job status
- Display final result or error

### Purpose
The frontend exists **only as a demonstration client**.  
CloudQueue is a backend-focused system; UI complexity is intentionally avoided.

---

## Design Decisions

### Why SQS?
- Decouples request handling from processing
- Provides natural backpressure
- Enables retries and DLQ handling
- Eliminates tight coupling between services

### Why DynamoDB?
- Serverless and horizontally scalable
- Supports conditional updates for idempotency
- Cost-effective with on-demand billing
- Simple schema for job state storage

### Why Separate API and Worker Lambdas?
- Different trust boundaries
- Different failure modes
- Enforces least-privilege IAM
- Reflects real microservice ownership patterns

### Why Mixed Languages (Java + Python)?
- Demonstrates interoperability across services
- Java for strongly-typed API contracts
- Python for concise background processing
- Common in real production environments

---

## Cost Awareness

This project is designed to remain within **AWS Free Tier** usage:

- AWS Lambda (low invocation volume)
- DynamoDB on-demand billing
- SQS standard queue
- Minimal CloudWatch logging
- Static frontend hosting

A billing budget alert is configured to prevent accidental overspend.

---

## Repository Structure

```
cloudqueue/
├── cloudqueue-api/
│   └── Java Lambda (job submission)
├── cloudqueue-worker/
│   └── Python Lambda (job processing)
├── frontend/          # planned
│   └── static demo UI
├── diagrams/
│   └── architecture.png
└── README.md
```

---

## What This Project Demonstrates

- Event-driven system design
- Asynchronous processing patterns
- Queue-based decoupling
- Idempotency and safe retries
- Cloud-native, serverless architecture
- Infrastructure cost awareness
- Debugging across service boundaries

This project intentionally prioritizes **correctness and architecture** over surface-level features.

---

## Status

**Core backend complete.**  
Frontend planned for demonstration purposes.

---

## Disclaimer

This project is intended for **learning and demonstration** purposes.  
The public API is not authenticated and should not be used for production workloads.
