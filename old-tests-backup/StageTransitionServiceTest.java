package com.ats;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.CandidateSource;
import com.ats.model.CandidateStage;
import com.ats.model.GradeBand;
import com.ats.model.InterviewRound;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;
import com.ats.model.TransitionResult;
import com.ats.service.RecruitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link com.ats.service.StageTransitionService} via {@link RecruitmentService}
 * verifying valid transitions, illegal stage jumps, terminal locks, score gates (BR-05, BR-06, BR-07),
 * and headcount reservation/release (BR-30, BR-31).
 */
class StageTransitionServiceTest {

    private RecruitmentService service;

    @BeforeEach
    void setUp() {
        service = new RecruitmentService();
        service.registerRequisition(
                "REQ-101",
                "Java Microservices Developer",
                GradeBand.G3,
                1,
                "Bengaluru",
                new BigDecimal("1200000"),
                new BigDecimal("1800000"),
                "Vikram Desai",
                LocalDate.of(2026, 3, 1)
        );
        service.registerCandidate(
                "CAN-201",
                "Aarav Kulkarni",
                30,
                new BigDecimal("1100000"),
                new BigDecimal("1500000"),
                new BigDecimal("4.0"),
                CandidateSource.REFERRAL
        );
        service.applyCandidate("APP-301", "CAN-201", "REQ-101", LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("BR-04: Allows full valid primary stage lifecycle from SCREENED to JOINED")
    void shouldAllowValidPrimaryStageLifecycleToJoined() {
        assertStageTransition("APP-301", CandidateStage.L1);
        assertStageTransition("APP-301", CandidateStage.L2);
        assertStageTransition("APP-301", CandidateStage.HR);
        assertStageTransition("APP-301", CandidateStage.OFFER_DRAFT);
        assertStageTransition("APP-301", CandidateStage.OFFER_APPROVED);
        assertStageTransition("APP-301", CandidateStage.OFFERED);
        assertStageTransition("APP-301", CandidateStage.ACCEPTED);
        assertStageTransition("APP-301", CandidateStage.JOINED);

        Application app = service.getRequiredApplication("APP-301");
        assertEquals(CandidateStage.JOINED, app.getCurrentStage());
        assertEquals(9, app.getStageHistory().size()); // initial SCREENED + 8 transitions
    }

    @Test
    @DisplayName("BR-04: Allows valid side-state transitions (ON_HOLD, WAITLIST, DECLINED, WITHDRAWN)")
    void shouldAllowValidAlternativeStages() {
        // SCREENED -> ON_HOLD -> L1 -> L2 -> WAITLIST -> OFFER_DRAFT -> OFFER_APPROVED -> OFFERED -> DECLINED
        assertStageTransition("APP-301", CandidateStage.ON_HOLD);
        assertStageTransition("APP-301", CandidateStage.L1);
        assertStageTransition("APP-301", CandidateStage.L2);
        assertStageTransition("APP-301", CandidateStage.WAITLIST);
        assertStageTransition("APP-301", CandidateStage.OFFER_DRAFT);

        Requisition req = service.getRequiredRequisition("REQ-101");
        assertEquals(1, req.getReservedHeadcount());

        assertStageTransition("APP-301", CandidateStage.OFFER_APPROVED);
        assertStageTransition("APP-301", CandidateStage.OFFERED);
        assertStageTransition("APP-301", CandidateStage.DECLINED);

        // BR-30: Declining an offer releases reserved headcount immediately
        assertEquals(0, req.getReservedHeadcount());
        assertEquals(CandidateStage.DECLINED, service.getRequiredApplication("APP-301").getCurrentStage());
    }

    @Test
    @DisplayName("BR-04: Rejects illegal stage jumps and backward transitions with ILLEGAL_STAGE_TRANSITION")
    void shouldRejectIllegalStageTransitionsWithReasonCode() {
        // Cannot skip from SCREENED directly to HR
        AtsException skipToHr = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.HR)
        );
        assertEquals(ReasonCode.ILLEGAL_STAGE_TRANSITION, skipToHr.getReasonCode());

        // Move to L1 -> L2 legally, then attempt illegal backward transition L2 -> L1
        service.transitionStage("APP-301", CandidateStage.L1);
        service.transitionStage("APP-301", CandidateStage.L2);

        AtsException backwardEx = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.L1)
        );
        assertEquals(ReasonCode.ILLEGAL_STAGE_TRANSITION, backwardEx.getReasonCode());

        // Cannot jump from L2 directly to OFFERED (must go HR -> OFFER_DRAFT -> OFFER_APPROVED -> OFFERED)
        AtsException skipOfferApprovalEx = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.OFFERED)
        );
        assertEquals(ReasonCode.ILLEGAL_STAGE_TRANSITION, skipOfferApprovalEx.getReasonCode());
    }

    @Test
    @DisplayName("BR-04: Rejects same-stage and terminal-stage transitions with exact reason codes")
    void shouldRejectSameStageAndTerminalTransitions() {
        // Same stage: SCREENED -> SCREENED
        AtsException sameStageEx = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.SCREENED)
        );
        assertEquals(ReasonCode.SAME_STAGE_TRANSITION, sameStageEx.getReasonCode());

        // Move to terminal stage REJECTED, then attempt REJECTED -> L1
        service.transitionStage("APP-301", CandidateStage.REJECTED);

        AtsException terminalLockedEx = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.L1)
        );
        assertEquals(ReasonCode.TERMINAL_STAGE_LOCKED, terminalLockedEx.getReasonCode());
    }

    @Test
    @DisplayName("BR-05, BR-06, BR-07: Enforces interview score thresholds and immediate rejection below 2.0")
    void shouldEnforceScoreThresholdsAndImmediateRejection() {
        service.transitionStage("APP-301", CandidateStage.L1);

        // L1 score 2.9 fails L1 -> L2 gate (requires >= 3.0)
        service.recordInterviewScore("APP-301", InterviewRound.L1, new BigDecimal("2.9"));
        AtsException lowL1Ex = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.L2)
        );
        assertEquals(ReasonCode.INSUFFICIENT_L1_SCORE, lowL1Ex.getReasonCode());

        // Exact boundary L1 score 3.0 passes L1 -> L2 gate (BR-05)
        service.recordInterviewScore("APP-301", InterviewRound.L1, new BigDecimal("3.0"));
        assertStageTransition("APP-301", CandidateStage.L2);

        // L2 score 3.4 fails L2 -> HR gate (requires >= 3.5)
        service.recordInterviewScore("APP-301", InterviewRound.L2, new BigDecimal("3.4"));
        AtsException lowL2Ex = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-301", CandidateStage.HR)
        );
        assertEquals(ReasonCode.INSUFFICIENT_L2_SCORE, lowL2Ex.getReasonCode());

        // Exact boundary L2 score 3.5 passes L2 -> HR gate (BR-06)
        service.recordInterviewScore("APP-301", InterviewRound.L2, new BigDecimal("3.5"));
        assertStageTransition("APP-301", CandidateStage.HR);

        // BR-07: HR score 1.9 (< 2.0) immediately moves application to REJECTED
        TransitionResult autoReject = service.recordInterviewScore("APP-301", InterviewRound.HR, new BigDecimal("1.9"));
        assertEquals(ReasonCode.AUTO_REJECTED_LOW_SCORE, autoReject.reasonCode());
        assertEquals(CandidateStage.REJECTED, service.getRequiredApplication("APP-301").getCurrentStage());
    }

    @Test
    @DisplayName("BR-31: Rejects OFFER_DRAFT with HEADCOUNT_EXHAUSTED when requisition headcount is full")
    void shouldRejectOfferDraftWhenHeadcountIsExhausted() {
        // Advance APP-301 to OFFER_DRAFT, occupying the 1 headcount on REQ-101
        service.transitionStage("APP-301", CandidateStage.L1);
        service.transitionStage("APP-301", CandidateStage.L2);
        service.transitionStage("APP-301", CandidateStage.HR);
        service.transitionStage("APP-301", CandidateStage.OFFER_DRAFT);

        // Register second candidate & advance to HR on the same requisition
        service.registerCandidate(
                "CAN-202",
                "Diya Hegde",
                30,
                new BigDecimal("1150000"),
                new BigDecimal("1550000"),
                new BigDecimal("4.2"),
                CandidateSource.PORTAL
        );
        service.applyCandidate("APP-302", "CAN-202", "REQ-101", LocalDate.of(2026, 3, 3));
        service.transitionStage("APP-302", CandidateStage.L1);
        service.transitionStage("APP-302", CandidateStage.L2);
        service.transitionStage("APP-302", CandidateStage.HR);

        AtsException exhaustedEx = assertThrows(
                AtsException.class,
                () -> service.transitionStage("APP-302", CandidateStage.OFFER_DRAFT)
        );
        assertEquals(ReasonCode.HEADCOUNT_EXHAUSTED, exhaustedEx.getReasonCode());
    }

    private void assertStageTransition(String appId, CandidateStage expectedTarget) {
        TransitionResult result = service.transitionStage(appId, expectedTarget);
        assertTrue(result.allowed());
        assertEquals(ReasonCode.SUCCESS, result.reasonCode());
        assertEquals(expectedTarget, result.toStage());
        assertEquals(expectedTarget, service.getRequiredApplication(appId).getCurrentStage());
    }
}
