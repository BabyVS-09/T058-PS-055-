package com.team058.ats.service;

import com.team058.ats.model.*;

import java.time.LocalDate;
import java.util.*;

public class OfferApprovalMatrix {

    public static final double OUT_OF_BAND_VP_THRESHOLD = 0.10; // Exactly 10% above max CTC
    public static final long CFO_TOTAL_COST_THRESHOLD = 5000000L; // 50 Lakhs total cost trigger

    public static class OfferDraftResult {
        public final boolean success;
        public final OfferRecord offerRecord;
        public final String reasonCode;

        public OfferDraftResult(boolean success, OfferRecord offerRecord, String reasonCode) {
            this.success = success;
            this.offerRecord = offerRecord;
            this.reasonCode = reasonCode;
        }
    }

    public static OfferDraftResult draftOffer(Candidate candidate, Requisition requisition,
                                              LocalDate draftDate, long offerCtc,
                                              long joiningBonus, long buyoutAmount) {
        long maxCtc = requisition.getMaxCtc();
        boolean outOfBand = offerCtc > maxCtc;

        // Calculate recruitment fees based on source
        long externalFee = 0;
        if ("AGENCY".equalsIgnoreCase(candidate.getSource())) {
            externalFee = Math.round(offerCtc * 0.0833); // Standard 1 month gross agency fee (8.33%)
        } else if ("REFERRAL".equalsIgnoreCase(candidate.getSource())) {
            externalFee = 50000L; // Fixed employee referral bonus
        }

        long totalCost = offerCtc + joiningBonus + buyoutAmount + externalFee;

        // Determine Approval Chain
        List<String> chain = new ArrayList<>();
        chain.add("HM");
        chain.add("HR_HEAD");

        // EC4: Check if offer exceeds max_ctc
        if (outOfBand) {
            double percentAbove = (double) (offerCtc - maxCtc) / (double) maxCtc;
            chain.add("VP");
            // If > 10% (e.g. 10.01%) OR total cost >= threshold, CFO is mandatory
            if (percentAbove > OUT_OF_BAND_VP_THRESHOLD || totalCost >= CFO_TOTAL_COST_THRESHOLD) {
                chain.add("CFO");
            }
        } else if (totalCost >= CFO_TOTAL_COST_THRESHOLD) {
            chain.add("VP");
            chain.add("CFO");
        }

        OfferRecord offer = new OfferRecord(
                candidate.getCandId(),
                requisition.getReqId(),
                draftDate,
                offerCtc,
                joiningBonus,
                outOfBand,
                totalCost,
                chain,
                "DRAFT",
                draftDate
        );

        return new OfferDraftResult(true, offer, "SUCCESS");
    }

    public static class ApprovalResult {
        public final boolean success;
        public final boolean fullyApproved;
        public final boolean rejected;
        public final String reasonCode;

        public ApprovalResult(boolean success, boolean fullyApproved, boolean rejected, String reasonCode) {
            this.success = success;
            this.fullyApproved = fullyApproved;
            this.rejected = rejected;
            this.reasonCode = reasonCode;
        }
    }

    public static ApprovalResult processApproval(OfferRecord offer, String approverRole,
                                                 String decision, LocalDate actionDate,
                                                 WorkingCalendar calendar) {
        if ("REJECTED".equals(offer.getFinalStatus()) || "APPROVED".equals(offer.getFinalStatus())) {
            return new ApprovalResult(false, false, false, "OFFER_ALREADY_RESOLVED");
        }

        String nextRole = offer.getNextRequiredApprover();
        if (nextRole == null) {
            return new ApprovalResult(false, false, false, "NO_PENDING_APPROVAL");
        }

        // Sequence validation: Must match nextRole
        if (!nextRole.equalsIgnoreCase(approverRole)) {
            return new ApprovalResult(false, false, false, "OUT_OF_SEQUENCE_APPROVAL");
        }

        // EC6: Check timeout across weekend and holidays (2 working days)
        // If approval is submitted after more than 2 working days, flag timeout or escalation
        LocalDate baselineDate = offer.getStatusDate();
        int workingDaysElapsed = calendar.countWorkingDaysBetween(baselineDate.plusDays(1), actionDate);
        if (workingDaysElapsed > 2) {
            // Approval timed out or escalated
            offer.addApprovalResult(approverRole, "ESCALATED_" + decision.toUpperCase());
            offer.setStatusDate(actionDate);
            if ("REJECT".equalsIgnoreCase(decision)) {
                offer.setFinalStatus("REJECTED");
                return new ApprovalResult(true, false, true, "APPROVAL_REJECTED_AFTER_TIMEOUT");
            }
            if (offer.isFullyApproved()) {
                offer.setFinalStatus("APPROVED");
                return new ApprovalResult(true, true, false, "APPROVED_WITH_ESCALATION");
            }
            offer.setFinalStatus("PENDING_APPROVAL");
            return new ApprovalResult(true, false, false, "APPROVED_WITH_ESCALATION");
        }

        offer.addApprovalResult(approverRole, decision.toUpperCase());
        offer.setStatusDate(actionDate);

        if ("REJECT".equalsIgnoreCase(decision)) {
            offer.setFinalStatus("REJECTED");
            return new ApprovalResult(true, false, true, "APPROVAL_REJECTED");
        }

        if (offer.isFullyApproved()) {
            offer.setFinalStatus("APPROVED");
            return new ApprovalResult(true, true, false, "SUCCESS");
        }

        offer.setFinalStatus("PENDING_APPROVAL");
        return new ApprovalResult(true, false, false, "SUCCESS");
    }

    public static boolean isOfferAcceptanceValid(OfferRecord offer, LocalDate acceptanceDate) {
        if (offer.getExtendedDate() == null) return false;
        // EC5: Day 7 is valid; Day 8 is expired!
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(offer.getExtendedDate(), acceptanceDate);
        return daysDiff <= 7;
    }
}
