package com.ats;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.Candidate;
import com.ats.model.CandidateSource;
import com.ats.model.CandidateStage;
import com.ats.model.GradeBand;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;
import com.ats.model.RequisitionStatus;
import com.ats.service.RecruitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link RecruitmentService} covering BR-01, BR-02, BR-03,
 * duplicate ID prevention, missing field validation, and CTC range validation.
 */
class RecruitmentServiceTest {

    private RecruitmentService service;

    @BeforeEach
    void setUp() {
        service = new RecruitmentService();
    }

    @Test
    @DisplayName("BR-01: Successfully registers a valid job requisition")
    void shouldRegisterRequisitionSuccessfully() {
        Requisition requisition = service.registerRequisition(
                "REQ-101",
                "Senior Java Developer",
                GradeBand.G4,
                3,
                "Bengaluru",
                new BigDecimal("1500000"),
                new BigDecimal("2200000"),
                "Vikram Desai",
                LocalDate.of(2026, 3, 1)
        );

        assertNotNull(requisition);
        assertEquals("REQ-101", requisition.getRequisitionId());
        assertEquals("Senior Java Developer", requisition.getRole());
        assertEquals(GradeBand.G4, requisition.getGradeBand());
        assertEquals(3, requisition.getHeadcount());
        assertEquals(3, requisition.getAvailableHeadcount());
        assertEquals("Bengaluru", requisition.getLocation());
        assertEquals(new BigDecimal("1500000.00"), requisition.getMinCtc());
        assertEquals(new BigDecimal("2200000.00"), requisition.getMaxCtc());
        assertEquals("Vikram Desai", requisition.getHiringManager());
        assertEquals(LocalDate.of(2026, 3, 1), requisition.getOpeningDate());
        assertEquals(RequisitionStatus.OPEN, requisition.getStatus());
    }

    @Test
    @DisplayName("BR-02: Successfully registers a valid candidate")
    void shouldRegisterCandidateSuccessfully() {
        Candidate candidate = service.registerCandidate(
                "CAN-201",
                "Aarav Kulkarni",
                30,
                new BigDecimal("1200000"),
                new BigDecimal("1650000"),
                new BigDecimal("4.5"),
                CandidateSource.REFERRAL
        );

        assertNotNull(candidate);
        assertEquals("CAN-201", candidate.getCandidateId());
        assertEquals("Aarav Kulkarni", candidate.getName());
        assertEquals(30, candidate.getNoticePeriodDays());
        assertEquals(new BigDecimal("1200000.00"), candidate.getCurrentCtc());
        assertEquals(new BigDecimal("1650000.00"), candidate.getExpectedCtc());
        assertEquals(new BigDecimal("4.5"), candidate.getExperienceYears());
        assertEquals(CandidateSource.REFERRAL, candidate.getSource());
    }

    @Test
    @DisplayName("BR-03: Successfully applies a registered candidate to a valid open requisition")
    void shouldApplyCandidateToValidRequisition() {
        service.registerRequisition(
                "REQ-101",
                "Backend Engineer",
                GradeBand.G3,
                2,
                "Pune",
                new BigDecimal("1000000"),
                new BigDecimal("1600000"),
                "Meera Nair",
                LocalDate.of(2026, 3, 1)
        );
        service.registerCandidate(
                "CAN-201",
                "Diya Hegde",
                45,
                new BigDecimal("950000"),
                new BigDecimal("1350000"),
                new BigDecimal("3.2"),
                CandidateSource.PORTAL
        );

        Application application = service.applyCandidate(
                "APP-301",
                "CAN-201",
                "REQ-101",
                LocalDate.of(2026, 3, 5)
        );

        assertNotNull(application);
        assertEquals("APP-301", application.getApplicationId());
        assertEquals("CAN-201", application.getCandidateId());
        assertEquals("REQ-101", application.getRequisitionId());
        assertEquals(GradeBand.G3, application.getTargetGrade());
        assertEquals(CandidateStage.SCREENED, application.getCurrentStage());
        assertEquals(LocalDate.of(2026, 3, 5), application.getApplicationDate());
        assertEquals(1, application.getStageHistory().size());
    }

    @Test
    @DisplayName("Duplicate Prevention: Rejects duplicate requisition ID and duplicate candidate ID")
    void shouldRejectDuplicateRequisitionAndCandidateIds() {
        service.registerRequisition(
                "REQ-101",
                "Java Engineer",
                GradeBand.G2,
                1,
                "Chennai",
                new BigDecimal("800000"),
                new BigDecimal("1200000"),
                "Anil Sharma",
                LocalDate.of(2026, 3, 1)
        );

        AtsException dupReqEx = assertThrows(AtsException.class, () -> service.registerRequisition(
                "REQ-101",
                "Cloud Engineer",
                GradeBand.G3,
                2,
                "Bengaluru",
                new BigDecimal("1100000"),
                new BigDecimal("1500000"),
                "Anil Sharma",
                LocalDate.of(2026, 3, 2)
        ));
        assertEquals(ReasonCode.DUPLICATE_REQUISITION_ID, dupReqEx.getReasonCode());

        service.registerCandidate(
                "CAN-201",
                "Rohan Shetty",
                15,
                new BigDecimal("700000"),
                new BigDecimal("950000"),
                new BigDecimal("2.0"),
                CandidateSource.AGENCY
        );

        AtsException dupCanEx = assertThrows(AtsException.class, () -> service.registerCandidate(
                "CAN-201",
                "Another Candidate",
                30,
                new BigDecimal("800000"),
                new BigDecimal("1100000"),
                new BigDecimal("3.0"),
                CandidateSource.PORTAL
        ));
        assertEquals(ReasonCode.DUPLICATE_CANDIDATE_ID, dupCanEx.getReasonCode());
    }

    @Test
    @DisplayName("Validation: Rejects missing required fields for requisitions, candidates, and applications")
    void shouldRejectMissingRequiredFields() {
        // Missing role on requisition
        AtsException missingRoleEx = assertThrows(AtsException.class, () -> service.registerRequisition(
                "REQ-200",
                "   ",
                GradeBand.G3,
                2,
                "Bengaluru",
                new BigDecimal("1000000"),
                new BigDecimal("1500000"),
                "Vikram Desai",
                LocalDate.of(2026, 3, 1)
        ));
        assertEquals(ReasonCode.MISSING_REQUIRED_FIELD, missingRoleEx.getReasonCode());

        // Missing name on candidate
        AtsException missingNameEx = assertThrows(AtsException.class, () -> service.registerCandidate(
                "CAN-300",
                "",
                30,
                new BigDecimal("900000"),
                new BigDecimal("1200000"),
                new BigDecimal("3.0"),
                CandidateSource.INTERNAL
        ));
        assertEquals(ReasonCode.MISSING_REQUIRED_FIELD, missingNameEx.getReasonCode());

        // Null source on candidate
        AtsException missingSourceEx = assertThrows(AtsException.class, () -> service.registerCandidate(
                "CAN-301",
                "Sneha Rao",
                30,
                new BigDecimal("900000"),
                new BigDecimal("1200000"),
                new BigDecimal("3.0"),
                null
        ));
        assertEquals(ReasonCode.MISSING_REQUIRED_FIELD, missingSourceEx.getReasonCode());
    }

    @Test
    @DisplayName("Validation: Rejects invalid CTC values (min > max, zero/negative CTCs) and non-positive headcount")
    void shouldRejectInvalidCtcValuesAndHeadcount() {
        // minCtc > maxCtc
        AtsException invertedBandEx = assertThrows(AtsException.class, () -> service.registerRequisition(
                "REQ-401",
                "Data Engineer",
                GradeBand.G4,
                2,
                "Hyderabad",
                new BigDecimal("2000000"),
                new BigDecimal("1500000"),
                "Priya Menon",
                LocalDate.of(2026, 3, 1)
        ));
        assertEquals(ReasonCode.INVALID_CTC, invertedBandEx.getReasonCode());

        // Negative minCtc on requisition
        AtsException negativeReqCtcEx = assertThrows(AtsException.class, () -> service.registerRequisition(
                "REQ-402",
                "Data Engineer",
                GradeBand.G4,
                2,
                "Hyderabad",
                new BigDecimal("-500000"),
                new BigDecimal("1500000"),
                "Priya Menon",
                LocalDate.of(2026, 3, 1)
        ));
        assertEquals(ReasonCode.INVALID_CTC, negativeReqCtcEx.getReasonCode());

        // Non-positive headcount
        AtsException zeroHeadcountEx = assertThrows(AtsException.class, () -> service.registerRequisition(
                "REQ-403",
                "Data Engineer",
                GradeBand.G4,
                0,
                "Hyderabad",
                new BigDecimal("1200000"),
                new BigDecimal("1800000"),
                "Priya Menon",
                LocalDate.of(2026, 3, 1)
        ));
        assertEquals(ReasonCode.INVALID_HEADCOUNT, zeroHeadcountEx.getReasonCode());

        // Negative current CTC on candidate
        AtsException negativeCurrentCtcEx = assertThrows(AtsException.class, () -> service.registerCandidate(
                "CAN-401",
                "Kiran Kumar",
                30,
                new BigDecimal("-1000"),
                new BigDecimal("900000"),
                new BigDecimal("2.0"),
                CandidateSource.PORTAL
        ));
        assertEquals(ReasonCode.INVALID_CTC, negativeCurrentCtcEx.getReasonCode());

        // Zero expected CTC on candidate
        AtsException zeroExpectedCtcEx = assertThrows(AtsException.class, () -> service.registerCandidate(
                "CAN-402",
                "Kiran Kumar",
                30,
                new BigDecimal("600000"),
                BigDecimal.ZERO,
                new BigDecimal("2.0"),
                CandidateSource.PORTAL
        ));
        assertEquals(ReasonCode.INVALID_CTC, zeroExpectedCtcEx.getReasonCode());
    }
}
