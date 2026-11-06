# T058 PS-055 — Week 3 faculty dataset integration pack

This folder bundles the faculty-provided PS-055 sample dataset alongside the existing Week 3 Maven project.

## Important status
This is an **integration-ready working copy**, not a claim that all 397 faculty commands have been successfully evaluated. The Week 3 implementation's original command grammar differs from the faculty pack grammar. The faculty pack also requires CSV-driven master-data loading, availability-window scheduling, and four exact CSV output artifacts (`transitions.csv`, `interviews.csv`, `offers.csv`, `pipeline_report.csv`). Those are not fully implemented by the original Week 3 engine. Do not submit this ZIP as a verified final evaluation build without completing and testing those adapters.

## Existing Week 3 functionality retained
The original Java 17/Maven source, domain models, services, parser, tests, decision log, traceability document, and sample command files are retained unchanged so prior Week 3 work is not lost.

## Faculty input dataset included
`faculty-sample-data/` contains:
- `requisitions.csv`
- `candidates.csv`
- `interviewers.csv`
- `availability.csv`
- `holidays.csv`
- `commands.txt`
- `README.md` with schemas, command grammar, planted edge cases, and exact output headers.

## Run the existing Week 3 project
From the project root:
```bash
mvn clean test
mvn package
java -jar target/recruitment-ats-1.0.0.jar sample-data/happy-path.txt
```

## Faculty pack requirements to finish before final submission
1. Load the five faculty CSV master-data files (including `target_join_date`, `department`, and per-interviewer availability windows).
2. Translate faculty commands (`SCREEN`, `SCHEDULE`, `SCORE`, `APPROVE`, `EXTEND_OFFER`, `ACCEPT`, `DECLINE`, `JOIN`, `BUYOUT`, `REPORT`) to the engine's domain operations using the faculty argument order.
3. Respect the explicitly supplied panel IDs and their grade eligibility; do not silently substitute another interviewer.
4. Apply faculty holiday and availability data, 10-working-day scheduling horizon, 15-minute gaps, daily limits, and exact availability boundary semantics.
5. Implement approval timeouts/escalation using working days and the supplied holiday calendar.
6. Resolve headcount contention/waitlisting deterministically by L2 score then application date.
7. Generate the four CSV output files with the exact column order in the faculty README.
8. Run the complete 397-command pack and add regression tests for EC1–EC8 and all listed extra cases.

The faculty README is the source of truth for field formats and planted scenarios. Keep output rows in command order where specified and do not change CSV headers.
