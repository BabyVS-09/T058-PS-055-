# Decision Log — PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix

- **Team ID:** T058
- **Team Members:** Baby V S (`1AT24CG012`) and Bhagyashree Rama Naik (`1AT24CG013`)
- **Language & Build:** Java 17, Apache Maven, JUnit 5

This document records both the architectural decisions established in the Week 1 Design Document (`docs/design-document.pdf`) and the concrete implementation decisions made for the Week 2 core backend engine.

---

## Part 1: Baseline Decisions from Week 1 Design Document

| Decision ID | Topic | Decision & Rationale |
| :--- | :--- | :--- |
| **DEC-01** | `OUT_OF_BAND` Threshold (BR-16) | Compare original expected CTC against `bandMax * 1.10` before offer caps are applied. Exactly 10% above `bandMax` is **not** flagged `OUT_OF_BAND`; `10.01%` above is flagged (planned for Week 3/4 `OfferCalculator`). |
| **DEC-02** | CTC Band Validation & Floor/Cap Precedence (BR-01, BR-15) | Requisition registration strictly enforces `minCtc > 0`, `maxCtc > 0`, and `minCtc <= maxCtc` (`ReasonCode.INVALID_CTC`). Monetary values use `java.math.BigDecimal` scaled to 2 decimal places (`RoundingMode.HALF_UP`) to eliminate floating-point drift. |
| **DEC-03** | Interview Window Boundaries (BR-10, BR-13) | An interview slot ending exactly at an availability window boundary will be treated as valid in Week 3 `InterviewScheduler`, provided overlap checks and the 15-minute inter-interview gap pass. |
| **DEC-04** | Offer Validity Cutoff (BR-29) | Offer acceptance through Day 7 at `23:59` is valid; at Day 8 `00:00`, an unaccepted offer becomes `WITHDRAWN`. |
| **DEC-05** | Approval SLA & Non-Skippable Approvers (BR-28) | Approval SLAs count working days excluding weekends and holidays; `VP` and `CFO` are never auto-skipped on timeout. |
| **DEC-06** | Total Cost Definition (BR-23) | Total hiring cost is computed as `offeredCTC + applicableFee + joiningBonus` when evaluating the `bandMax * 1.15` CFO approval rule. |
| **DEC-07** | Deterministic Headcount Reservation (BR-31, BR-33) | Headcount reservations occur deterministically when entering `OFFER_DRAFT` and are released immediately if an offer-holding application moves to `DECLINED`, `WITHDRAWN`, `REJECTED`, or `ON_HOLD`. |
| **DEC-08** | Separation of Candidate, Application, and Requisition State | `Candidate` stores person-level profile data (`currentCtc`, `expectedCtc`, `noticePeriodDays`, `experienceYears`, `source`). `Application` owns stage progression, interview scores, and audit timestamps (`LocalDateTime`) for a specific `Requisition`, allowing one candidate to apply to distinct requisitions independently. |

---

## Part 2: Week 2 Implementation Decisions & Assumptions

| Decision ID | Topic | Decision & Rationale |
| :--- | :--- | :--- |
| **DEC-09** | Pipe-Delimited (`\|`) Command File Syntax (BR-38) | Commands in `sample-data/happy-path.txt` use `\|` as the field delimiter rather than whitespace. This allows multi-word job roles (`Senior Java Backend Engineer`), hiring manager names (`Vikram Desai`), and candidate names (`Aarav Kulkarni`) without requiring escape sequences or quotation marks. Blank lines and `#` comment lines are ignored. |
| **DEC-10** | Candidate Identifier Flexibility (BR-02) | `Candidate` stores both `candidateId` and `name`. Both `RecruitmentService` and `CommandFileParser` support registering candidates with explicit `(candidateId, name, ...)` as well as a single identifier `(candidateIdOrName, ...)` so callers using either convention work seamlessly. |
| **DEC-11** | Candidate CTC & Fresher Support (BR-02) | `currentCtc >= 0` is allowed so entry-level/fresher candidates with `0` current CTC can be registered, whereas negative `currentCtc` is rejected with `INVALID_CTC`. `expectedCtc` must be strictly `> 0`. |
| **DEC-12** | Duplicate Prevention Rules (BR-01, BR-02, BR-03) | In-memory `LinkedHashMap` stores preserve deterministic insertion order while rejecting duplicate `requisitionId` (`DUPLICATE_REQUISITION_ID`), duplicate `candidateId` (`DUPLICATE_CANDIDATE_ID`), duplicate `applicationId` (`DUPLICATE_APPLICATION_ID`), and duplicate applications by the same candidate to the same requisition (`DUPLICATE_CANDIDATE_APPLICATION`). |
| **DEC-13** | Explicit 14-Stage State Machine & Terminal Locks (BR-04) | Every application starts in `SCREENED`. Forward progression follows `SCREENED -> L1 -> L2 -> HR -> OFFER_DRAFT -> OFFER_APPROVED -> OFFERED -> ACCEPTED -> JOINED`. Controlled transitions to `WAITLIST`, `ON_HOLD`, `DECLINED`, `REJECTED`, and `WITHDRAWN` are explicitly enumerated in `StageTransitionService`. `JOINED`, `REJECTED`, `DECLINED`, and `WITHDRAWN` are terminal (`TERMINAL_STAGE_LOCKED`); no-op self-transitions return `SAME_STAGE_TRANSITION`; stage skips or backward jumps return `ILLEGAL_STAGE_TRANSITION`. |
| **DEC-14** | Configurable Interview Score Gates in Week 2 (BR-05, BR-06, BR-07) | `StageTransitionService` enforces `L1 >= 3.0` (`INSUFFICIENT_L1_SCORE`), `L2 >= 3.5` (`INSUFFICIENT_L2_SCORE`), and immediate `REJECTED` on any round score `< 2.0` (`AUTO_REJECTED_LOW_SCORE`) whenever scores are recorded via `RECORD_SCORE` / `recordInterviewScore(...)`. By default in Week 2 (`enforceMandatoryScores = false`), pure stage-transition flows without recorded scores are also allowed so standalone stage tests work prior to Week 3 `InterviewScheduler`; passing `new StageTransitionService(true)` enables strict mandatory-score mode for later weeks. |
| **DEC-15** | Early Headcount Reservation Hook (BR-30, BR-31) | Moving into `OFFER_DRAFT` checks `requisition.hasAvailableHeadcount()`. If all positions are already reserved, the transition is rejected with `HEADCOUNT_EXHAUSTED` so the recruiter can place the candidate on `WAITLIST`. Moving to `DECLINED`, `WITHDRAWN`, `REJECTED`, or `ON_HOLD` from an offer-holding stage immediately releases the reserved headcount. |
