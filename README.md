# Recruitment ATS with Interview Scheduling and Offer Approval Matrix
**Team ID:** T058  
**Project ID:** PS-055  
**Technology:** Java 17 + Maven 3.8+ + JUnit 5 (Jupiter)

---

## 1. Executive Overview

This repository contains the complete enterprise recruitment Applicant Tracking System (ATS) developed for Team T058 (Project ID: PS-055). The system provides deterministic command processing, calendar-aware multi-stage interview scheduling, stage scoring validation, dynamic offer calculation with multi-tier approval chains, and automated headcount/waitlist management with pipeline health reporting.

---

## 2. Directory Structure

```text
recruitment-ats/
├── pom.xml                                   # Maven configuration with JUnit 5 & exec plugins
├── README.md                                 # Full technical documentation and Windows CMD guide
├── data/                                     # Input dataset directory
│   ├── requisitions.csv                      # 8 job requisitions
│   ├── candidates.csv                        # 40 candidate profiles
│   ├── interviewers.csv                      # 12 interviewers with grades and departments
│   ├── availability.csv                      # 543 availability windows (includes deduplication)
│   ├── holidays.csv                          # 5 corporate/public holidays
│   └── commands.txt                          # 221 sequential recruitment workflow commands
├── output/                                   # Generated output CSV files
│   ├── transitions.csv                       # State transitions audit trail with reason codes
│   ├── interviews.csv                        # Scheduled & scored interviews
│   ├── offers.csv                            # Offer draft, approval chain, costs & final status
│   └── pipeline_report.csv                   # Point-in-time requisition pipeline funnel reports
└── src/
    ├── main/java/com/team058/ats/
    │   ├── Main.java                         # Command-line entry point
    │   ├── model/
    │   │   ├── Candidate.java                # Candidate domain model & attributes
    │   │   ├── CandidateState.java           # Comprehensive state machine enum
    │   │   ├── Requisition.java              # Requisition domain model & band metrics
    │   │   ├── Interviewer.java              # Panelist model with L2 grade verification
    │   │   ├── AvailabilityWindow.java       # Slot model with equals/hash for deduplication
    │   │   ├── Holiday.java                  # Company holiday definition
    │   │   ├── TransitionRecord.java         # transitions.csv output row model
    │   │   ├── InterviewRecord.java          # interviews.csv output row model
    │   │   ├── OfferRecord.java              # offers.csv output row model
    │   │   └── PipelineReportRecord.java     # pipeline_report.csv output row model
    │   └── service/
    │       ├── DataLoader.java               # Safe CSV parsing, validation & deduplication
    │       ├── WorkingCalendar.java          # Business calendar (Monday-Friday minus holidays)
    │       ├── SchedulingService.java        # Earliest slot, 15-min gap, window end validation
    │       ├── ScoringService.java           # Stage threshold evaluator & boundary handler
    │       ├── OfferApprovalMatrix.java      # Out-of-band, CFO trigger, sequence & timeout checks
    │       └── WorkflowEngine.java           # Sequential command processing & transition engine
    └── test/java/com/team058/ats/
        └── AtsComprehensiveTest.java         # Complete JUnit 5 test suite covering EC1-EC8 + edge cases
```

---

## 3. Business Rules & Architecture

### A. Input Dataset Processing
- **Deduplication:** Availability entries with identical `(interviewer_id, date, start_time, end_time)` are deduplicated prior to scheduling. Out of 543 rows in `availability.csv`, 18 duplicates are identified and merged, yielding 525 unique availability windows.
- **Sequential Execution:** Commands in `commands.txt` are executed strictly in sequential order. No command is reordered or processed prematurely.

### B. Calendar & Scheduling Rules
- **Working Days:** Monday to Friday, excluding corporate holidays listed in `holidays.csv`.
- **Working Hours:** 09:00 to 18:00.
- **Search Horizon:** Earliest slot within the next 10 working days from the command date.
- **Conflict Prevention:** Panelists cannot have overlapping interview commitments.
- **15-Minute Gap:** Mandatory 15-minute buffer between successive interviews for any panelist (`EC3`).
- **Window End Boundary:** An interview is valid if it ends exactly at the availability window end (`EC2`).
- **L2 Panel Validation:** L2 technical interviews require Senior panelists (`L4`, `L5`, `L6`). Panelists with grade `L3` or Junior are rejected with `INVALID_PANEL`.

### C. Interview Scoring Thresholds
- **L1 Technical:** Minimum passing score is `3.0` out of `5.0`.
- **L2 Advanced Technical:** Minimum passing score is `3.5` out of `5.0`.
- **HR Fit:** Minimum passing score is `2.0` out of `5.0`.
- **Boundary Handling (`EC1`):** Exactly `3.0` (L1), `3.5` (L2), `2.0` (HR) pass. Values strictly below (e.g., `2.99`, `3.49`, `1.99`) fail. Any score below `2.0` on L1/L2 is flagged with `SCORE_BELOW_2`.

### D. Compensation & Offer Approval Matrix
- **Salary Band Validation:** Requisitions specify `min_ctc` and `max_ctc`.
- **In-Band Offers:** `offer_ctc <= max_ctc`. Approval chain: `HM -> HR_HEAD`.
- **Out-of-Band (Up to 10% above max, `EC4`):** `offer_ctc <= max_ctc * 1.10`. Requires VP approval: `HM -> HR_HEAD -> VP`.
- **Out-of-Band (> 10% above max, `EC4`):** `offer_ctc > max_ctc * 1.10`. Requires CFO approval: `HM -> HR_HEAD -> VP -> CFO`.
- **High-Cost Trigger:** If total recruitment cost $\ge 50,00,000$, CFO approval is required regardless of band percentage.
- **Total Cost Calculation:**
  $$\text{Total Cost} = \text{Offer CTC} + \text{Joining Bonus} + \text{Buyout Amount} + \text{External Fee}$$
  * `AGENCY`: External fee is $8.33\%$ of Offer CTC (1 month gross fee).
  * `REFERRAL`: Fixed employee referral bonus of $50,000$.
  * `DIRECT` / `INTERNAL`: Zero external fee.
- **Sequence Enforcement:** Approvals must occur in strict sequence (`HM` first, then `HR_HEAD`, then `VP`, then `CFO`). Out-of-sequence submissions are rejected with `OUT_OF_SEQUENCE_APPROVAL`.
- **Timeout & Escalation (`EC6`):** Approvals pending more than 2 working days trigger escalation logging.
- **Offer Acceptance Window (`EC5`):** Candidates have 7 calendar days to accept an extended offer. Day 7 is valid (`ACCEPTED`); Day 8 is expired (`OFFER_EXPIRED`).
- **Headcount Contention & WAITLIST (`EC7`):** When free headcount reaches 0, additional candidates passing HR move to `WAITLIST` (`HEADCOUNT_FULL_WAITLIST`). If an offered candidate declines, waitlisted candidates become eligible.
- **Internal Transfers (`EC8`):** Candidates with source `INTERNAL` have 0 notice period, zero agency/referral fee, and can have expected CTC below current CTC without penalty.

---

## 4. Planted Edge Cases Summary

| Case Code | Scenario Description | Expected & Implemented Behavior |
|:---|:---|:---|
| **EC1** | Boundary interview scores | `3.0` on L1, `3.5` on L2, `2.0` on HR pass; `2.99`, `3.49`, `1.99` fail |
| **EC2** | Interview ending exactly at window end | Slot 14:00-15:00 within window 14:00-15:00 is valid |
| **EC3** | 15-minute interviewer gap | Second interview cannot start earlier than 15 mins after first ends |
| **EC4** | Expected CTC 10% vs 10.01% above band | Exactly 10% above requires VP; 10.01% above triggers CFO |
| **EC5** | Offer acceptance on Day 7 vs Day 8 | Day 7 acceptance succeeds; Day 8 rejected with `OFFER_EXPIRED` |
| **EC6** | Approval timeout across weekend/holidays | 2 working days evaluated excluding weekends and holiday list |
| **EC7** | Headcount contention & WAITLIST | Offer attempts when free headcount = 0 transition to `WAITLIST` |
| **EC8** | Internal candidate zero notice period | Instant onboarding with zero agency fees and lower expected CTC |

---

## 5. Required Output CSV Files

1. **`transitions.csv`**
   ```csv
   line_no,date,cand_id,req_id,from_state,to_state,result,reason_code
   1,2026-10-02,CAND-001,REQ-001,NONE,APPLIED,SUCCESS,SUCCESS
   ...
   ```
2. **`interviews.csv`**
   ```csv
   cand_id,stage,scheduled_date,start_time,end_time,panel,result
   CAND-001,L1,2026-10-05,10:00,11:00,INT-001,PASSED
   ...
   ```
3. **`offers.csv`**
   ```csv
   cand_id,req_id,draft_date,offer_ctc,joining_bonus,out_of_band,total_cost,approval_chain,approval_results,final_status,status_date
   CAND-001,REQ-001,2026-10-12,2600000,100000,FALSE,2700000,HM->HR_HEAD,HM:APPROVE;HR_HEAD:APPROVE,JOINED,2026-11-02
   ...
   ```
4. **`pipeline_report.csv`**
   ```csv
   report_date,req_id,age_days,at_risk,free_headcount,screened,l1,l2,hr,offer_draft,offer_approved,offered,accepted,joined,waitlist,offers_pending_with
   2026-10-15,REQ-001,14,FALSE,0,0,0,0,0,0,0,0,0,2,0,NONE
   ...
   ```

---

## 6. Execution Instructions for Windows CMD

### Prerequisites
- Java Development Kit (JDK 17 or higher) installed and configured on `PATH`.
- Apache Maven (3.8 or higher) installed and configured on `PATH`.
- Verify in Windows CMD:
  ```cmd
  java -version
  mvn -version
  ```

### Step 1: Open Windows Command Prompt
Navigate to the project root directory:
```cmd
cd path\to\recruitment-ats
```

### Step 2: Run Complete Unit Test Suite
To run all JUnit 5 tests covering all business rules and edge cases:
```cmd
mvn clean test
```

### Step 3: Compile and Execute ATS Engine
To process the dataset and generate all four output CSV files:
```cmd
mvn compile exec:java
```
Or pass custom dataset and output directories:
```cmd
mvn compile exec:java -Dexec.args="data output"
```

### Step 4: Verify Output Files
Confirm that all 4 CSV files have been written to `output\`:
```cmd
dir output
type output\pipeline_report.csv
```
