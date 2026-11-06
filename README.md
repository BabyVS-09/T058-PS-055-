# Faculty dataset compatibility note

The faculty sample pack is bundled in `faculty-sample-data/`. See
`FACULTY_DATASET_INTEGRATION.md` for the compatibility status and the exact
remaining integration work. The original Week 3 project is preserved; its
legacy command parser is not yet a drop-in evaluator for the faculty command
format.

---

# PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix

## Team Details

| Field | Details |
| :--- | :--- |
| **Problem Statement** | PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix |
| **Team ID** | `T058` |
| **Team Member 1** | Baby V S (`1AT24CG012`) |
| **Team Member 2** | Bhagyashree Rama Naik (`1AT24CG013`) |
| **Selected Language** | Java 17 |
| **Build Tool** | Apache Maven |
| **Testing Framework** | JUnit 5 (`junit-jupiter 5.10.2`) |
| **Application Type** | Command-line backend engine (no GUI / web interface) |
| **Deadline** | 6 November 2026 |

---

## Project Overview

This repository implements the **Week 3 Full Implementation** of **PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix**. It is a robust, deterministic, command-file-driven Java 17 application with zero external runtime database or GUI dependencies.

### Implemented Business Capabilities (Week 3 Full Scope)

1. **Job Requisition Management (`BR-01`, `BR-34`, `BR-35`)**:
   - Requisition registration with ID, role, grade band (`G1`–`G6`), positive headcount, `BigDecimal` min/max CTC, hiring manager, and opening date.
   - Requisition ageing SLA: transitions to `AT_RISK` after 45 days; automatically closes after 90 days unless headcount is reserved; on auto-closure moves non-offered candidates to `ON_HOLD`.

2. **Candidate & Application Management (`BR-02`, `BR-03`, `BR-33`)**:
   - Candidate registration with ID, name, notice period days, `BigDecimal` current/expected CTC, years of experience, and source (`REFERRAL`, `PORTAL`, `AGENCY`, `INTERNAL`).
   - Prevents duplicate requisition, candidate, and application IDs, and duplicate applications by the same candidate to the same requisition.
   - Enforces notice period `> 90` days constraint: target joining date must be at least the candidate's notice period after offer date.

3. **14-Stage Application State Machine (`BR-04`)**:
   - Stages: `SCREENED`, `L1`, `L2`, `HR`, `OFFER_DRAFT`, `OFFER_APPROVED`, `OFFERED`, `ACCEPTED`, `DECLINED`, `JOINED`, `REJECTED`, `WAITLIST`, `ON_HOLD`, `WITHDRAWN`.
   - Prevents illegal jumps, backward skips, no-op transitions, and locked terminal states (`JOINED`, `REJECTED`, `DECLINED`, `WITHDRAWN`).

4. **Interview Scheduling Engine (`BR-08` to `BR-13`) — Module A**:
   - Exact round durations: L1 (60 minutes), L2 (45 minutes), HR (30 minutes).
   - Maximum 3 interviews per interviewer per day.
   - Minimum 15-minute gap between interviews; zero overlapping bookings.
   - L2 panel grade hierarchy: interviewer grade must be at least one level higher than candidate target grade (`PANEL_GRADE_INSUFFICIENT`).
   - Searches earliest slot across next 10 working days, excluding weekends and supplied holidays.
   - Interview ending exactly at availability-window boundary (e.g. 18:00) is valid (`DEC-03`).
   - Returns `NO_SLOT` if unavailable without changing candidate stage.

5. **Score Validation Gates (`BR-05`, `BR-06`, `BR-07`, `BR-14`) — Module B**:
   - L1 passing score `>= 3.0`; L2 passing score `>= 3.5`.
   - Strict score range `0.0` to `5.0` (`INVALID_SCORE`).
   - Scores below `2.0` trigger immediate auto-rejection (`AUTO_REJECTED_LOW_SCORE`).

6. **Offer CTC Calculation (`BR-15`, `BR-16`) — Module C**:
   - Uses `BigDecimal` with 2 decimal places (`HALF_UP`) for all financial arithmetic.
   - Maximum permitted CTC is lower of `current CTC * 1.40` and requisition `bandMax`.
   - Requisition `bandMin` is enforced as non-negotiable floor (`DEC-17`).
   - Flags `OUT_OF_BAND` when expected CTC exceeds `bandMax` by more than 10% (requires VP approval). Exactly 10% above is not out of band; 10.01% is out of band.

7. **Joining Bonuses & Recruitment Costs (`BR-17` to `BR-24`) — Module D**:
   - Early joiner bonus: ₹50,000 for notice period `<= 30` days joining within 45 days of offer.
   - Notice buyout bonus: ₹1,00,000 for notice period `> 60` days with buyout.
   - Buyout reimbursement capped at `current CTC / 12`.
   - `INTERNAL` candidates receive zero joining bonus and zero buyout bonus.
   - Recruitment fees: `REFERRAL` = ₹25,000; `AGENCY` = 8.33% of offered CTC.
   - Total cost = `offeredCTC + recruitmentFee + joiningBonus + buyoutReimbursement`.
   - Total cost `> bandMax * 1.15` triggers mandatory CFO approval.

8. **Sequential Approval Matrix & SLA Tracking (`BR-25` to `BR-28`) — Module E**:
   - Dynamic routing based on thresholds:
     - Offered `<= bandMax`: Hiring Manager.
     - `bandMax < Offered <= 1.10 * bandMax`: Hiring Manager -> HR Head.
     - `Offered > 1.10 * bandMax` OR `OUT_OF_BAND`: Hiring Manager -> HR Head -> VP.
     - Total cost `> 1.15 * bandMax`: Appends CFO approval.
   - Sequential execution; rejection immediately terminates approval chain.
   - 2 working days SLA per approver, excluding weekends and holidays.
   - SLA breach marks `TIMED_OUT` and escalates; VP and CFO are **never** auto-skipped.

9. **Headcount & Offer Validity (`BR-29` to `BR-32`) — Module F**:
   - Offer validity: 7 calendar days (Day 7 at 23:59:59 is valid; Day 8 at 00:00:00 is invalid / expired).
   - `OFFER_DRAFT` reserves 1 headcount; declined/expired offers release reserved headcount.
   - Contending candidate waitlist ranked by L2 score descending, then application date ascending.

10. **Command-File Interface (`BR-38`)**:
    - Executes pipe-delimited (`|`) batch command files and displays `[SUCCESS]` or `[ERROR]` console feedback.

---

## Prerequisites

Ensure the following tools are installed and available on your system `PATH`:

- **Java Development Kit (JDK) 17+**
  ```cmd
  java -version
  javac -version
  ```
- **Apache Maven 3.8+**
  ```cmd
  mvn -version
  ```

---

## Project Structure

```text
T058-PS-055/
├── pom.xml
├── README.md
├── decision-log.md
├── metadata.json
├── docs/
│   ├── business-rule-traceability.md
│   └── design-document.pdf
├── sample-data/
│   ├── happy-path.txt
│   └── edge-cases.txt
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── ats/
    │               ├── Main.java
    │               ├── model/
    │               │   ├── Application.java
    │               │   ├── ApprovalMatrix.java
    │               │   ├── ApprovalRole.java
    │               │   ├── ApprovalStatus.java
    │               │   ├── ApprovalStep.java
    │               │   ├── AtsException.java
    │               │   ├── Candidate.java
    │               │   ├── CandidateSource.java
    │               │   ├── CandidateStage.java
    │               │   ├── GradeBand.java
    │               │   ├── Interviewer.java
    │               │   ├── InterviewRound.java
    │               │   ├── InterviewSchedulingResult.java
    │               │   ├── InterviewSlot.java
    │               │   ├── Offer.java
    │               │   ├── OfferCalculationResult.java
    │               │   ├── OfferStatus.java
    │               │   ├── ReasonCode.java
    │               │   ├── Requisition.java
    │               │   ├── RequisitionStatus.java
    │               │   ├── StageTransitionRecord.java
    │               │   └── TransitionResult.java
    │               ├── parser/
    │               │   ├── CommandExecutionResult.java
    │               │   └── CommandFileParser.java
    │               └── service/
    │                   ├── ApprovalService.java
    │                   ├── InterviewScheduler.java
    │                   ├── OfferCalculator.java
    │                   ├── RecruitmentService.java
    │                   └── StageTransitionService.java
    └── test/
        └── java/
            └── com/
                └── ats/
                    ├── ApprovalServiceTest.java
                    ├── CommandFileParserTest.java
                    ├── InterviewSchedulerTest.java
                    ├── OfferCalculatorTest.java
                    ├── OfferExpiryAndHeadcountTest.java
                    ├── RecruitmentServiceTest.java
                    └── StageTransitionServiceTest.java
```

---

## Windows Command Prompt (`cmd.exe`) Instructions

Open **Windows Command Prompt** (`cmd.exe`) and navigate into the project directory:

```cmd
cd T058-PS-055
```

### 1. Compile Codebase
```cmd
mvn clean compile
```

### 2. Execute Full JUnit 5 Test Suite (46 Tests)
```cmd
mvn test
```

### 3. Package Runnable JAR
```cmd
mvn package
```

### 4. Execute Happy-Path Command File
```cmd
java -cp target\classes com.ats.Main sample-data\happy-path.txt
```
Or with packaged JAR:
```cmd
java -jar target\recruitment-ats-1.0.0.jar sample-data\happy-path.txt
```

### 5. Execute Edge-Cases & Validation Command File
```cmd
java -cp target\classes com.ats.Main sample-data\edge-cases.txt
```
Or with packaged JAR:
```cmd
java -jar target\recruitment-ats-1.0.0.jar sample-data\edge-cases.txt
```

---

## Command File Reference

| Command | Pipe-Delimited Syntax |
| :--- | :--- |
| `REGISTER_REQUISITION` | `REGISTER_REQUISITION \| <reqId> \| <role> \| <G1..G6> \| <headcount> \| <location> \| <minCtc> \| <maxCtc> \| <hiringManager> \| <YYYY-MM-DD>` |
| `REGISTER_CANDIDATE` | `REGISTER_CANDIDATE \| <candidateId> \| <name> \| <noticeDays> \| <currentCtc> \| <expectedCtc> \| <expYears> \| <REFERRAL\|PORTAL\|AGENCY\|INTERNAL>` |
| `APPLY` | `APPLY \| <applicationId> \| <candidateId> \| <requisitionId> \| <YYYY-MM-DD>` |
| `SCHEDULE_INTERVIEW` | `SCHEDULE_INTERVIEW \| <applicationId> \| <L1\|L2\|HR> \| <optional YYYY-MM-DD>` |
| `RECORD_SCORE` | `RECORD_SCORE \| <applicationId> \| <L1\|L2\|HR> \| <0.0..5.0>` |
| `TRANSITION_STAGE` | `TRANSITION_STAGE \| <applicationId> \| <targetStage>` |
| `DRAFT_OFFER` | `DRAFT_OFFER \| <offerId> \| <applicationId> \| <offerDate: YYYY-MM-DD> \| <targetJoiningDate: YYYY-MM-DD> \| <buyout: true/false> \| <optional buyoutAmount>` |
| `REQUEST_APPROVAL` | `REQUEST_APPROVAL \| <approvalId> \| <offerId> \| <optional requestDate: YYYY-MM-DD>` |
| `RECORD_APPROVAL` | `RECORD_APPROVAL \| <approvalId> \| <HIRING_MANAGER\|HR_HEAD\|VP\|CFO> \| <APPROVE\|REJECT> \| <remarks>` |
| `ACCEPT_OFFER` | `ACCEPT_OFFER \| <applicationId> \| <optional YYYY-MM-DDTHH:mm:ss>` |
| `DECLINE_OFFER` | `DECLINE_OFFER \| <applicationId> \| <reason>` |
| `EXPIRE_OFFER` | `EXPIRE_OFFER \| <applicationId> \| <optional YYYY-MM-DDTHH:mm:ss>` |
| `RELEASE_HEADCOUNT` | `RELEASE_HEADCOUNT \| <requisitionId>` |
| `SUMMARY` | `SUMMARY` |
