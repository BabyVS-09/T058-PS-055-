package com.team058.ats;

import com.team058.ats.model.*;
import com.team058.ats.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AtsComprehensiveTest {

    private DataLoader.LoadedData testData;
    private WorkingCalendar calendar;

    @BeforeEach
    public void setup() throws Exception {
        File dataDir = new File("data");
        testData = DataLoader.loadAll(dataDir);
        calendar = new WorkingCalendar(testData.holidays);
    }

    @Test
    @DisplayName("Verify Dataset Loading and Deduplication (543 availability rows loaded, duplicates handled)")
    public void testDatasetLoading() {
        assertEquals(8, testData.requisitions.size(), "Should load 8 requisitions");
        assertEquals(40, testData.candidates.size(), "Should load 40 candidates");
        assertEquals(12, testData.interviewers.size(), "Should load 12 interviewers");
        assertEquals(5, testData.holidays.size(), "Should load 5 holidays");
        assertTrue(testData.duplicateAvailabilityCount > 0, "Duplicate availability rows should be detected and deduplicated");
        assertEquals(543, testData.availabilities.size() + testData.duplicateAvailabilityCount, "Total availability rows read should be 543");
    }

    @Test
    @DisplayName("EC1: Boundary interview scores (3.0 L1, 3.5 L2, 2.0 HR pass; below fails)")
    public void testEC1_BoundaryInterviewScores() {
        // L1 boundary: 3.0 PASS, 2.99 FAIL
        assertTrue(ScoringService.evaluate("L1", 3.0).passed, "L1 3.0 should pass");
        assertFalse(ScoringService.evaluate("L1", 2.99).passed, "L1 2.99 should fail");

        // L2 boundary: 3.5 PASS, 3.49 FAIL
        assertTrue(ScoringService.evaluate("L2", 3.5).passed, "L2 3.5 should pass");
        assertFalse(ScoringService.evaluate("L2", 3.49).passed, "L2 3.49 should fail");

        // HR boundary: 2.0 PASS, 1.99 FAIL
        assertTrue(ScoringService.evaluate("HR", 2.0).passed, "HR 2.0 should pass");
        assertFalse(ScoringService.evaluate("HR", 1.99).passed, "HR 1.99 should fail");
    }

    @Test
    @DisplayName("EC2: Interview ending exactly at availability window end")
    public void testEC2_WindowEndBoundary() {
        List<AvailabilityWindow> singleWindow = new ArrayList<>();
        LocalDate testDate = LocalDate.of(2026, 10, 14); // Wednesday
        singleWindow.add(new AvailabilityWindow("INT-001", testDate, LocalTime.of(14, 0), LocalTime.of(15, 0)));

        SchedulingService scheduler = new SchedulingService(calendar, testData.interviewers, singleWindow);
        SchedulingService.ScheduleResult res = scheduler.scheduleInterview("CAND-007", "L1", "INT-001", testDate, 60);

        assertTrue(res.success, "Interview should fit in window ending exactly at 15:00");
        assertEquals(LocalTime.of(14, 0), res.startTime);
        assertEquals(LocalTime.of(15, 0), res.endTime);
    }

    @Test
    @DisplayName("EC3: 15-minute interviewer gap between interviews")
    public void testEC3_InterviewerGap() {
        List<AvailabilityWindow> windows = new ArrayList<>();
        LocalDate testDate = LocalDate.of(2026, 10, 14);
        // Window 10:00 to 13:00 (3 hours)
        windows.add(new AvailabilityWindow("INT-001", testDate, LocalTime.of(10, 0), LocalTime.of(13, 0)));

        SchedulingService scheduler = new SchedulingService(calendar, testData.interviewers, windows);
        // First 60-min interview: 10:00 to 11:00
        SchedulingService.ScheduleResult res1 = scheduler.scheduleInterview("CAND-001", "L1", "INT-001", testDate, 60);
        assertTrue(res1.success);
        assertEquals(LocalTime.of(10, 0), res1.startTime);
        assertEquals(LocalTime.of(11, 0), res1.endTime);

        // Second interview must NOT start at 11:00; it must start at 11:15 due to the 15-minute gap requirement
        SchedulingService.ScheduleResult res2 = scheduler.scheduleInterview("CAND-002", "L1", "INT-001", testDate, 60);
        assertTrue(res2.success);
        assertEquals(LocalTime.of(11, 15), res2.startTime, "Second interview must enforce a 15-minute gap after 11:00");
        assertEquals(LocalTime.of(12, 15), res2.endTime);
    }

    @Test
    @DisplayName("EC4: Expected CTC exactly 10% (VP) and 10.01% (CFO) above band maximum")
    public void testEC4_OutOfBandApprovalChains() {
        Requisition req = testData.requisitions.get("REQ-001"); // max_ctc: 30,00,000
        Candidate c1 = testData.candidates.get("CAND-009");
        Candidate c2 = testData.candidates.get("CAND-010");

        // Exactly 10% above 30,00,000 = 33,00,000 -> Out-of-band requiring VP
        long exact10Percent = 3300000L;
        OfferApprovalMatrix.OfferDraftResult r1 = OfferApprovalMatrix.draftOffer(c1, req, LocalDate.of(2026, 10, 16), exact10Percent, 100000, 0);
        assertTrue(r1.offerRecord.isOutOfBand(), "Offer should be out of band");
        assertEquals(List.of("HM", "HR_HEAD", "VP"), r1.offerRecord.getApprovalChain(), "Exact 10% above should require HM, HR_HEAD, VP");

        // 10.01% above 30,00,000 = 33,00,300 -> Requires CFO
        long above10Percent = 3300300L;
        OfferApprovalMatrix.OfferDraftResult r2 = OfferApprovalMatrix.draftOffer(c2, req, LocalDate.of(2026, 10, 16), above10Percent, 100000, 0);
        assertTrue(r2.offerRecord.isOutOfBand(), "Offer should be out of band");
        assertEquals(List.of("HM", "HR_HEAD", "VP", "CFO"), r2.offerRecord.getApprovalChain(), "Exceeding 10% above max must require CFO");
    }

    @Test
    @DisplayName("EC5: Offer acceptance on day 7 versus day 8")
    public void testEC5_OfferValidityDays() {
        OfferRecord offer = new OfferRecord("CAND-011", "REQ-001", LocalDate.of(2026, 10, 17), 2500000, 50000, false, 2550000, List.of("HM", "HR_HEAD"), "APPROVED", LocalDate.of(2026, 10, 19));
        offer.setExtendedDate(LocalDate.of(2026, 10, 20));

        // Day 7: 2026-10-27 -> Valid
        assertTrue(OfferApprovalMatrix.isOfferAcceptanceValid(offer, LocalDate.of(2026, 10, 27)), "Acceptance on Day 7 should be valid");

        // Day 8: 2026-10-28 -> Expired
        assertFalse(OfferApprovalMatrix.isOfferAcceptanceValid(offer, LocalDate.of(2026, 10, 28)), "Acceptance on Day 8 should be expired");
    }

    @Test
    @DisplayName("EC6: Approval timeout across weekend and holidays (2 working days)")
    public void testEC6_ApprovalTimeoutAcrossWeekendAndHolidays() {
        OfferRecord offer = new OfferRecord("CAND-013", "REQ-001", LocalDate.of(2026, 10, 21), 2800000, 100000, false, 2900000, List.of("HM", "HR_HEAD"), "DRAFT", LocalDate.of(2026, 10, 21));

        // Oct 21 is Wednesday. Oct 22 (Thu) = 1 working day. Oct 23 (Fri) = 2 working days.
        // Oct 24 (Sat) is Dussehra (holiday). Oct 25 (Sun) is weekend. Oct 26 (Mon) = 3 working days elapsed.
        // Action on Oct 27 is past 2 working days -> Escalation/Timeout triggered!
        OfferApprovalMatrix.ApprovalResult res = OfferApprovalMatrix.processApproval(offer, "HM", "APPROVE", LocalDate.of(2026, 10, 27), calendar);
        assertTrue(res.success);
        assertTrue(offer.getApprovalResults().get(0).startsWith("HM:ESCALATED"), "Approval should be flagged with escalation after timeout");
    }

    @Test
    @DisplayName("EC7: Last-headcount contention and WAITLIST")
    public void testEC7_HeadcountContentionAndWaitlist() {
        Requisition req = testData.requisitions.get("REQ-002"); // Headcount: 1
        assertEquals(1, req.getHeadcount());

        WorkflowEngine engine = new WorkflowEngine(testData);
        // Process CAND-014 to EXTENDED offer
        engine.processCommandLine(1, "APPLY 2026-10-08 CAND-014 REQ-002");
        engine.processCommandLine(2, "SCREEN 2026-10-09 CAND-014 PASS Architect A");
        engine.processCommandLine(3, "SCHEDULE 2026-10-10 CAND-014 L1 INT-004");
        engine.processCommandLine(4, "SCORE 2026-10-12 CAND-014 L1 4.5");
        engine.processCommandLine(5, "SCHEDULE 2026-10-13 CAND-014 L2 INT-007");
        engine.processCommandLine(6, "SCORE 2026-10-14 CAND-014 L2 4.8");
        engine.processCommandLine(7, "SCHEDULE 2026-10-15 CAND-014 HR INT-009");
        engine.processCommandLine(8, "SCORE 2026-10-16 CAND-014 HR 4.0");
        engine.processCommandLine(9, "DRAFT_OFFER 2026-10-17 CAND-014 4500000 200000 0");
        engine.processCommandLine(10, "APPROVE 2026-10-18 CAND-014 HM APPROVE");
        engine.processCommandLine(11, "APPROVE 2026-10-19 CAND-014 HR_HEAD APPROVE");
        engine.processCommandLine(12, "EXTEND_OFFER 2026-10-20 CAND-014");

        // Free headcount is now 0
        assertEquals(0, engine.getFreeHeadcount("REQ-002"), "Free headcount should be 0 after offer extended");

        // CAND-015 now passes HR and tries DRAFT_OFFER
        engine.processCommandLine(13, "APPLY 2026-10-09 CAND-015 REQ-002");
        engine.processCommandLine(14, "SCREEN 2026-10-10 CAND-015 PASS Architect B");
        engine.processCommandLine(15, "SCHEDULE 2026-10-12 CAND-015 L1 INT-004");
        engine.processCommandLine(16, "SCORE 2026-10-13 CAND-015 L1 4.2");
        engine.processCommandLine(17, "SCHEDULE 2026-10-14 CAND-015 L2 INT-007");
        engine.processCommandLine(18, "SCORE 2026-10-15 CAND-015 L2 4.4");
        engine.processCommandLine(19, "SCHEDULE 2026-10-16 CAND-015 HR INT-009");
        engine.processCommandLine(20, "SCORE 2026-10-17 CAND-015 HR 3.9");
        engine.processCommandLine(21, "DRAFT_OFFER 2026-10-20 CAND-015 4600000 200000 0");

        Candidate c15 = testData.candidates.get("CAND-015");
        assertEquals(CandidateState.WAITLIST, c15.getCurrentState(), "Candidate must enter WAITLIST when free headcount is 0");
    }

    @Test
    @DisplayName("EC8: Internal candidate with zero notice period & lower expected CTC")
    public void testEC8_InternalCandidateZeroNotice() {
        Candidate c = testData.candidates.get("CAND-016");
        assertEquals("INTERNAL", c.getSource());
        assertEquals(0, c.getNoticePeriodDays());
        assertTrue(c.getExpectedCtc() < c.getCurrentCtc(), "Expected CTC should be below current CTC");

        WorkflowEngine engine = new WorkflowEngine(testData);
        engine.processCommandLine(1, "APPLY 2026-10-09 CAND-016 REQ-003");
        engine.processCommandLine(2, "SCREEN 2026-10-10 CAND-016 PASS Internal transfer");
        engine.processCommandLine(3, "SCHEDULE 2026-10-12 CAND-016 L1 INT-003");
        engine.processCommandLine(4, "SCORE 2026-10-13 CAND-016 L1 4.0");
        engine.processCommandLine(5, "SCHEDULE 2026-10-14 CAND-016 L2 INT-001");
        engine.processCommandLine(6, "SCORE 2026-10-15 CAND-016 L2 4.1");
        engine.processCommandLine(7, "SCHEDULE 2026-10-16 CAND-016 HR INT-010");
        engine.processCommandLine(8, "SCORE 2026-10-17 CAND-016 HR 4.5");
        engine.processCommandLine(9, "DRAFT_OFFER 2026-10-18 CAND-016 1900000 0 0");
        engine.processCommandLine(10, "APPROVE 2026-10-19 CAND-016 HM APPROVE");
        engine.processCommandLine(11, "APPROVE 2026-10-20 CAND-016 HR_HEAD APPROVE");
        engine.processCommandLine(12, "EXTEND_OFFER 2026-10-21 CAND-016");
        engine.processCommandLine(13, "ACCEPT 2026-10-22 CAND-016");
        engine.processCommandLine(14, "JOIN 2026-10-23 CAND-016");

        assertEquals(CandidateState.JOINED, c.getCurrentState(), "Internal candidate should join smoothly");
    }

    @Test
    @DisplayName("Invalid L2 Panel rejection (Junior / L3 interviewer rejected for L2)")
    public void testInvalidL2Panel() {
        WorkflowEngine engine = new WorkflowEngine(testData);
        engine.processCommandLine(1, "APPLY 2026-10-13 CAND-026 REQ-001");
        engine.processCommandLine(2, "SCREEN 2026-10-14 CAND-026 PASS Valid");
        engine.processCommandLine(3, "SCHEDULE 2026-10-15 CAND-026 L1 INT-001");
        engine.processCommandLine(4, "SCORE 2026-10-16 CAND-026 L1 4.0");
        // INT-003 is L3, which is invalid for L2 panel
        engine.processCommandLine(5, "SCHEDULE 2026-10-17 CAND-026 L2 INT-003");

        Candidate c = testData.candidates.get("CAND-026");
        assertEquals(CandidateState.L1_PASSED, c.getCurrentState(), "State should not transition on invalid panel");
        TransitionRecord lastTrans = engine.getTransitions().get(engine.getTransitions().size() - 1);
        assertEquals("FAILURE", lastTrans.getResult());
        assertEquals("INVALID_PANEL", lastTrans.getReasonCode());
    }

    @Test
    @DisplayName("Out-of-sequence approval rejection")
    public void testOutOfSequenceApproval() {
        Requisition req = testData.requisitions.get("REQ-001");
        Candidate c = testData.candidates.get("CAND-001");
        OfferApprovalMatrix.OfferDraftResult r = OfferApprovalMatrix.draftOffer(c, req, LocalDate.of(2026, 10, 12), 2600000, 100000, 0);

        // Chain is HM -> HR_HEAD. Attempting HR_HEAD first should fail
        OfferApprovalMatrix.ApprovalResult res = OfferApprovalMatrix.processApproval(r.offerRecord, "HR_HEAD", "APPROVE", LocalDate.of(2026, 10, 13), calendar);
        assertFalse(res.success, "Out-of-sequence approval must fail");
        assertEquals("OUT_OF_SEQUENCE_APPROVAL", res.reasonCode);
    }

    @Test
    @DisplayName("Full command execution and CSV generation check")
    public void testFullExecutionAndCsvOutput() throws Exception {
        WorkflowEngine engine = new WorkflowEngine(testData);
        File commandsFile = new File("data/commands.txt");
        engine.processCommands(commandsFile);

        File outputDir = new File("output");
        engine.writeOutputCsvFiles(outputDir);

        File f1 = new File(outputDir, "transitions.csv");
        File f2 = new File(outputDir, "interviews.csv");
        File f3 = new File(outputDir, "offers.csv");
        File f4 = new File(outputDir, "pipeline_report.csv");

        assertTrue(f1.exists() && f1.length() > 0, "transitions.csv must exist and have content");
        assertTrue(f2.exists() && f2.length() > 0, "interviews.csv must exist and have content");
        assertTrue(f3.exists() && f3.length() > 0, "offers.csv must exist and have content");
        assertTrue(f4.exists() && f4.length() > 0, "pipeline_report.csv must exist and have content");
    }
}
