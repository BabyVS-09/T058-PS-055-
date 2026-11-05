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

---

## Project Description

This repository implements the **Week 2 Core Backend Engine** for **PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix**. It is a deterministic, command-file-driven Java 17 application that manages in-memory recruitment workflows without any database or graphical user interface.

### Week 2 Implemented Capabilities
1. **Job Requisition Registration (`BR-01`)**: Registers requisitions with ID, role, grade band (`G1`–`G6`), headcount, location, minimum and maximum CTC (`BigDecimal`), hiring manager, and opening date (`LocalDate`), enforcing positive headcount and `minCtc <= maxCtc`.
2. **Candidate Registration (`BR-02`)**: Registers candidates with ID, full name, notice period days, current CTC, expected CTC, years of experience (`BigDecimal`), and recruitment source (`REFERRAL`, `PORTAL`, `AGENCY`, `INTERNAL`).
3. **Candidate Application (`BR-03`)**: Links registered candidates to open requisitions, capturing application date (`LocalDate`), target grade (`GradeBand`), initial `SCREENED` stage, and timestamped stage history (`LocalDateTime`).
4. **14-Stage State Machine & Reason-Code Validation (`BR-04`)**: Supports all 14 stages (`SCREENED`, `L1`, `L2`, `HR`, `OFFER_DRAFT`, `OFFER_APPROVED`, `OFFERED`, `ACCEPTED`, `DECLINED`, `JOINED`, `REJECTED`, `WAITLIST`, `ON_HOLD`, `WITHDRAWN`) and rejects illegal stage transitions, backward jumps, no-op transitions, and transitions out of terminal states with deterministic `ReasonCode` values.
5. **Interview Score Gates & Headcount Hooks (`BR-05`, `BR-06`, `BR-07`, `BR-30`, `BR-31`)**: Supports recording round scores (`L1 >= 3.0`, `L2 >= 3.5`, `< 2.0` auto-rejection) and reserves/releases requisition headcount across offer stages, keeping the codebase ready for Week 3/4 interview scheduling, offer calculation, and approval matrix routing.
6. **Command-File Processing (`BR-38`)**: Parses pipe-delimited commands from `sample-data/happy-path.txt` (or any custom file path passed as a CLI argument) and prints clear `[SUCCESS]` and `[ERROR]` messages to standard output.

---

## Prerequisites

Ensure the following tools are installed and available on your Windows `PATH`:

- **Java Development Kit (JDK) 17** (or compatible JDK 17+)
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
├── docs/
│   └── design-document.pdf
├── sample-data/
│   └── happy-path.txt
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── ats/
    │               ├── Main.java
    │               ├── model/
    │               │   ├── Application.java
    │               │   ├── AtsException.java
    │               │   ├── Candidate.java
    │               │   ├── CandidateSource.java
    │               │   ├── CandidateStage.java
    │               │   ├── GradeBand.java
    │               │   ├── InterviewRound.java
    │               │   ├── ReasonCode.java
    │               │   ├── Requisition.java
    │               │   ├── RequisitionStatus.java
    │               │   ├── StageTransitionRecord.java
    │               │   └── TransitionResult.java
    │               ├── parser/
    │               │   ├── CommandExecutionResult.java
    │               │   └── CommandFileParser.java
    │               └── service/
    │                   ├── RecruitmentService.java
    │                   └── StageTransitionService.java
    └── test/
        └── java/
            └── com/
                └── ats/
                    ├── CommandFileParserTest.java
                    ├── RecruitmentServiceTest.java
                    └── StageTransitionServiceTest.java
```

> **Note on `docs/design-document.pdf`:** The official Week 1 Design Document for Team T058 (`PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix`) is included at `docs/design-document.pdf`.

---

## Windows Command Prompt (`cmd.exe`) Build, Test, and Run Instructions

Open **Windows Command Prompt** (`cmd.exe`) and navigate into the `T058-PS-055` directory:

```cmd
cd T058-PS-055
```

### 1. Compile the Project
```cmd
mvn clean compile
```

### 2. Run JUnit 5 Unit & Integration Tests
```cmd
mvn test
```

### 3. Run the Application (Default `sample-data\happy-path.txt`)
Using the compiled classes directly:
```cmd
java -cp target\classes com.ats.Main
```

Or passing an explicit command file path:
```cmd
java -cp target\classes com.ats.Main sample-data\happy-path.txt
```

Or using Maven Exec Plugin:
```cmd
mvn exec:java
```

Or packaging and running the standalone JAR:
```cmd
mvn package
java -jar target\recruitment-ats-1.0.0.jar sample-data\happy-path.txt
```

---

## Command File Syntax

Commands in `sample-data/happy-path.txt` are pipe-delimited (`|`). Blank lines and lines starting with `#` are ignored.

| Command | Format |
| :--- | :--- |
| `REGISTER_REQUISITION` | `REGISTER_REQUISITION \| <reqId> \| <role> \| <G1..G6> \| <headcount> \| <location> \| <minCtc> \| <maxCtc> \| <hiringManager> \| <YYYY-MM-DD>` |
| `REGISTER_CANDIDATE` | `REGISTER_CANDIDATE \| <candidateId> \| <name> \| <noticeDays> \| <currentCtc> \| <expectedCtc> \| <expYears> \| <REFERRAL\|PORTAL\|AGENCY\|INTERNAL>` |
| `APPLY` | `APPLY \| <applicationId> \| <candidateId> \| <requisitionId> \| <YYYY-MM-DD>` |
| `RECORD_SCORE` | `RECORD_SCORE \| <applicationId> \| <L1\|L2\|HR> \| <0.0..5.0>` |
| `TRANSITION_STAGE` | `TRANSITION_STAGE \| <applicationId> \| <targetStage>` |
| `SUMMARY` | `SUMMARY` |

---

## Sample Input (`sample-data/happy-path.txt` Excerpt)

```text
REGISTER_REQUISITION | REQ-101 | Senior Java Backend Engineer | G4 | 2 | Bengaluru | 1400000 | 2200000 | Vikram Desai | 2026-03-01
REGISTER_CANDIDATE | CAN-201 | Aarav Kulkarni | 30 | 1350000 | 1800000 | 5.5 | REFERRAL
APPLY | APP-301 | CAN-201 | REQ-101 | 2026-03-05
TRANSITION_STAGE | APP-301 | L1
RECORD_SCORE     | APP-301 | L1 | 4.2
TRANSITION_STAGE | APP-301 | L2
RECORD_SCORE     | APP-301 | L2 | 4.0
TRANSITION_STAGE | APP-301 | HR
RECORD_SCORE     | APP-301 | HR | 4.5
TRANSITION_STAGE | APP-301 | OFFER_DRAFT
TRANSITION_STAGE | APP-301 | OFFER_APPROVED
TRANSITION_STAGE | APP-301 | OFFERED
TRANSITION_STAGE | APP-301 | ACCEPTED
TRANSITION_STAGE | APP-301 | JOINED
SUMMARY
```

## Expected Console Output (`sample-data/happy-path.txt`)

```text
================================================================================
PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix
Team T058 | Baby V S (1AT24CG012) & Bhagyashree Rama Naik (1AT24CG013)
Command File: sample-data\happy-path.txt
================================================================================
[SUCCESS] Requisition registered: ID=REQ-101, Role="Senior Java Backend Engineer", Grade=G4, Headcount=2, Location=Bengaluru, CTC=1400000.00-2200000.00, Manager="Vikram Desai", Opened=2026-03-01
[SUCCESS] Requisition registered: ID=REQ-102, Role="QA Automation Engineer", Grade=G2, Headcount=1, Location=Hyderabad, CTC=700000.00-1100000.00, Manager="Meera Nair", Opened=2026-03-03
[SUCCESS] Candidate registered: ID=CAN-201, Name="Aarav Kulkarni", NoticeDays=30, CurrentCTC=1350000.00, ExpectedCTC=1800000.00, Experience=5.5 yrs, Source=REFERRAL
[SUCCESS] Candidate registered: ID=CAN-202, Name="Diya Hegde", NoticeDays=45, CurrentCTC=1250000.00, ExpectedCTC=1750000.00, Experience=4.8 yrs, Source=PORTAL
[SUCCESS] Candidate registered: ID=CAN-203, Name="Rohan Shetty", NoticeDays=15, CurrentCTC=780000.00, ExpectedCTC=950000.00, Experience=2.5 yrs, Source=AGENCY
[SUCCESS] Candidate registered: ID=CAN-204, Name="Sneha Rao", NoticeDays=0, CurrentCTC=1500000.00, ExpectedCTC=1850000.00, Experience=6.0 yrs, Source=INTERNAL
[SUCCESS] Application created: ID=APP-301, Candidate=CAN-201 ("Aarav Kulkarni"), Requisition=REQ-101 ("Senior Java Backend Engineer"), TargetGrade=G4, Stage=SCREENED, AppliedOn=2026-03-05
[SUCCESS] Application created: ID=APP-302, Candidate=CAN-202 ("Diya Hegde"), Requisition=REQ-101 ("Senior Java Backend Engineer"), TargetGrade=G4, Stage=SCREENED, AppliedOn=2026-03-06
[SUCCESS] Application created: ID=APP-303, Candidate=CAN-203 ("Rohan Shetty"), Requisition=REQ-102 ("QA Automation Engineer"), TargetGrade=G2, Stage=SCREENED, AppliedOn=2026-03-07
[SUCCESS] Application created: ID=APP-304, Candidate=CAN-204 ("Sneha Rao"), Requisition=REQ-101 ("Senior Java Backend Engineer"), TargetGrade=G4, Stage=SCREENED, AppliedOn=2026-03-08
[SUCCESS] Stage updated: Application=APP-301, Transition=SCREENED -> L1 [SUCCESS]
[SUCCESS] L1 score 4.20 recorded for application APP-301.
[SUCCESS] Stage updated: Application=APP-301, Transition=L1 -> L2 [SUCCESS]
[SUCCESS] L2 score 4.00 recorded for application APP-301.
[SUCCESS] Stage updated: Application=APP-301, Transition=L2 -> HR [SUCCESS]
[SUCCESS] HR score 4.50 recorded for application APP-301.
[SUCCESS] Stage updated: Application=APP-301, Transition=HR -> OFFER_DRAFT [SUCCESS]
[SUCCESS] Stage updated: Application=APP-301, Transition=OFFER_DRAFT -> OFFER_APPROVED [SUCCESS]
[SUCCESS] Stage updated: Application=APP-301, Transition=OFFER_APPROVED -> OFFERED [SUCCESS]
[SUCCESS] Stage updated: Application=APP-301, Transition=OFFERED -> ACCEPTED [SUCCESS]
[SUCCESS] Stage updated: Application=APP-301, Transition=ACCEPTED -> JOINED [SUCCESS]
...
--------------------------------------------------------------------------------
ATS PIPELINE SNAPSHOT
Registered Requisitions : 2
  - REQ-101 | Senior Java Backend Engineer (G4) | Location=Bengaluru | Headcount=2 (Reserved=2, Free=0) | Status=OPEN
  - REQ-102 | QA Automation Engineer (G2) | Location=Hyderabad | Headcount=1 (Reserved=0, Free=1) | Status=OPEN
Registered Candidates   : 4
Active/Total Applications: 4
  - APP-301 | Candidate=CAN-201 | Requisition=REQ-101 | Grade=G4 | CurrentStage=JOINED | Transitions=8
  - APP-302 | Candidate=CAN-202 | Requisition=REQ-101 | Grade=G4 | CurrentStage=WAITLIST | Transitions=3
  - APP-303 | Candidate=CAN-203 | Requisition=REQ-102 | Grade=G2 | CurrentStage=DECLINED | Transitions=8
  - APP-304 | Candidate=CAN-204 | Requisition=REQ-101 | Grade=G4 | CurrentStage=OFFER_DRAFT | Transitions=4
--------------------------------------------------------------------------------
================================================================================
Execution Complete | Commands Processed: 43 | Succeeded: 43 | Rejected/Errors: 0
================================================================================
```

### Example Error Output on Invalid Input / Illegal Stage Transition

```text
[ERROR] Line 2 [DUPLICATE_REQUISITION_ID]: Requisition ID 'REQ-500' is already registered.
[ERROR] Line 3 [INVALID_CTC]: Minimum CTC (1800000) cannot exceed Maximum CTC (1500000).
[ERROR] Line 4 [MISSING_REQUIRED_FIELD]: Candidate name is required and cannot be blank.
[ERROR] Line 7 [ILLEGAL_STAGE_TRANSITION]: Illegal transition for application APP-500: SCREENED -> OFFERED. Allowed next stages from SCREENED: [L1, REJECTED, ON_HOLD, WITHDRAWN].
```
