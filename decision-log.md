# Decision Log — PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix

- **Team ID:** T058
- **Team Members:** Baby V S (`1AT24CG012`) and Bhagyashree Rama Naik (`1AT24CG013`)
- **Language & Build:** Java 17, Apache Maven, JUnit 5

This document records both the architectural decisions established in the Week 1 Design Document (`docs/design-document.pdf`), the Week 2 baseline core backend engine decisions, and the Week 3 business-rule implementations.

---

## Part 1: Baseline Decisions from Week 1 Design Document

| Decision ID | Topic | Decision & Rationale |
| :--- | :--- | :--- |
| **DEC-01** | `OUT_OF_BAND` Threshold (BR-16) | Compare original expected CTC against `bandMax * 1.10` before offer caps are applied. Exactly 10% above `bandMax` is **not** flagged `OUT_OF_BAND`; `10.01%` above is flagged (implemented in Week 3 `OfferCalculator`). |
| **DEC-02** | CTC Band Validation & Floor/Cap Precedence (BR-01, BR-15) | Requisition registration strictly enforces `minCtc > 0`, `maxCtc > 0`, and `minCtc <= maxCtc` (`ReasonCode.INVALID_CTC`). Monetary values use `java.math.BigDecimal` scaled to 2 decimal places (`RoundingMode.HALF_UP`) to eliminate floating-point drift. |
| **DEC-03** | Interview Window Boundaries (BR-10, BR-13) | An interview slot ending exactly at an availability window boundary will be treated as valid in Week 3 `InterviewScheduler`, provided overlap checks and the 15-minute inter-interview gap pass. |
| **DEC-04** | Offer Validity Cutoff (BR-29) | Offer acceptance through Day 7 at `23:59` is valid; at Day 8 `00:00`, an unaccepted offer becomes `WITHDRAWN` and releases reserved headcount. |
| **DEC-05** | Approval SLA & Non-Skippable Approvers (BR-28) | Approval SLAs count working days excluding weekends and holidays; `VP` and `CFO` are never auto-skipped on timeout. |
| **DEC-06** | Total Cost Definition (BR-23) | Total hiring cost is computed as `offeredCTC + applicableFee + joiningBonus + buyoutReimbursement` when evaluating the `bandMax * 1.15` CFO approval rule. |
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
| **DEC-14** | Configurable Interview Score Gates in Week 2 (BR-05, BR-06, BR-07) | `StageTransitionService` enforces `L1 >= 3.0` (`INSUFFICIENT_L1_SCORE`), `L2 >= 3.5` (`INSUFFICIENT_L2_SCORE`), and immediate `REJECTED` on any round score `< 2.0` (`AUTO_REJECTED_LOW_SCORE`) whenever scores are recorded via `RECORD_SCORE` / `recordInterviewScore(...)`. |
| **DEC-15** | Early Headcount Reservation Hook (BR-30, BR-31) | Moving into `OFFER_DRAFT` checks `requisition.hasAvailableHeadcount()`. If all positions are already reserved, the transition is rejected with `HEADCOUNT_EXHAUSTED` so the recruiter can place the candidate on `WAITLIST`. Moving to `DECLINED`, `WITHDRAWN`, `REJECTED`, or `ON_HOLD` from an offer-holding stage immediately releases the reserved headcount. |

---

## Part 3: Week 3 Implementation Decisions & Ambiguity Resolutions

| Decision ID | Topic | Decision & Rationale |
| :--- | :--- | :--- |
| **DEC-16** | Interview Scheduling Slot Search & Panel Validation (BR-08 to BR-13) | `InterviewScheduler` checks L1 (60 min), L2 (45 min), and HR (30 min) durations. L2 round strictly checks that the panel has at least one interviewer whose `GradeBand` is `>= candidate.targetGrade + 1` (`PANEL_GRADE_INSUFFICIENT`). The search explores 15-minute slot intervals in the next 10 working days (Mon–Fri excluding holidays). Each interviewer is strictly limited to 3 interviews per day with 15-minute minimum inter-interview gaps. If no slot exists across the 10 working days, `NO_SLOT` is returned and the application remains in its current stage. |
| **DEC-17** | Resolution of Ambiguity Between CTC Floor and Cap (BR-15) | Requirement 3.C specifies: *"Maximum permitted CTC is the lower of current CTC × 1.40 and requisition band maximum. Apply requisition band minimum as the floor."* **Ambiguity:** What happens when a low-salaried candidate's `current CTC * 1.40` is lower than the requisition `bandMin`? **Decision:** The requisition band minimum is a non-negotiable floor representing the organization's approved grade pay scale. Therefore, if `min(current CTC * 1.40, bandMax) < bandMin`, the offered CTC is raised to `bandMin`. If `expected CTC` is higher than `bandMin`, offered CTC is `min(expected CTC, max(bandMin, min(current CTC * 1.40, bandMax)))`. |
| **DEC-18** | Exact Boundary Precision for `OUT_OF_BAND` (BR-16) | `bandMax.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP)` is evaluated. If `expected CTC <= 1.10 * bandMax`, the offer is within band. If `expected CTC > 1.10 * bandMax` (even by ₹0.01), `isOutOfBand` is `true` and VP approval is required. |
| **DEC-19** | Joining Bonus, Buyout Reimbursement, and Cost Breakdown (BR-17 to BR-24) | `INTERNAL` candidates receive ₹0 joining bonus and ₹0 buyout reimbursement. For external candidates: notice period `<= 30` days joining within 45 days of offer receives ₹50,000; notice period `> 60` days with buyout receives ₹1,00,000 bonus plus buyout reimbursement capped at `current CTC / 12`. Notice period exactly 60 days does not qualify for buyout bonus. Recruitment fees: `REFERRAL` = ₹25,000, `AGENCY` = 8.33% of offered CTC. Total hiring cost equals `offeredCTC + recruitmentFee + joiningBonus + buyoutReimbursement`. If total cost exceeds `bandMax * 1.15`, CFO approval step is appended. |
| **DEC-20** | Sequential Multi-Level Approval Matrix & SLA Timeouts (BR-25 to BR-28) | Approval order: HM (if `<= bandMax`), HM -> HR Head (if `bandMax < offered <= 1.10 * bandMax`), HM -> HR Head -> VP (if `> 1.10 * bandMax` or `OUT_OF_BAND`), plus CFO (if total cost `> 1.15 * bandMax`). Rejections immediately terminate the chain (`REJECTED`). SLA is 2 working days excluding weekends and holidays. On SLA timeout, HM and HR Head steps are marked `TIMED_OUT` and escalated; VP and CFO steps are **never** auto-skipped. |
| **DEC-21** | Offer Expiry, Headcount Contention & Requisition Ageing (BR-29 to BR-36) | Offers are valid for 7 calendar days; Day 7 at `23:59:59` is valid, Day 8 at `00:00:00` is invalid. Expired offers transition to `WITHDRAWN` and release headcount. In competing candidate scenarios, the first candidate to reach `OFFER_DRAFT` reserves the last vacancy; subsequent candidates are waitlisted and ranked by L2 score descending, then application date ascending. If an offer is declined or expired, the reserved headcount is released and available to waitlisted candidates. Requisition status becomes `AT_RISK` at 45 days, and automatically closes at 90 days unless headcount is reserved; on auto-closure, non-offered applications move to `ON_HOLD`. |
