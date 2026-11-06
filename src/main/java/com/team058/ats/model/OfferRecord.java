package com.team058.ats.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OfferRecord {
    private String candId;
    private String reqId;
    private LocalDate draftDate;
    private long offerCtc;
    private long joiningBonus;
    private boolean outOfBand;
    private long totalCost;
    private List<String> approvalChain; // e.g. ["HM", "HR_HEAD", "VP", "CFO"]
    private List<String> approvalResults; // e.g. ["HM:APPROVED", "HR_HEAD:APPROVED"]
    private String finalStatus; // DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, EXTENDED, ACCEPTED, DECLINED, JOINED, EXPIRED
    private LocalDate statusDate;
    private LocalDate extendedDate;

    public OfferRecord(String candId, String reqId, LocalDate draftDate, long offerCtc,
                       long joiningBonus, boolean outOfBand, long totalCost,
                       List<String> approvalChain, String finalStatus, LocalDate statusDate) {
        this.candId = candId;
        this.reqId = reqId;
        this.draftDate = draftDate;
        this.offerCtc = offerCtc;
        this.joiningBonus = joiningBonus;
        this.outOfBand = outOfBand;
        this.totalCost = totalCost;
        this.approvalChain = new ArrayList<>(approvalChain);
        this.approvalResults = new ArrayList<>();
        this.finalStatus = finalStatus;
        this.statusDate = statusDate;
        this.extendedDate = null;
    }

    public String getCandId() { return candId; }
    public String getReqId() { return reqId; }
    public LocalDate getDraftDate() { return draftDate; }
    public long getOfferCtc() { return offerCtc; }
    public long getJoiningBonus() { return joiningBonus; }
    public boolean isOutOfBand() { return outOfBand; }
    public long getTotalCost() { return totalCost; }
    public List<String> getApprovalChain() { return approvalChain; }
    public List<String> getApprovalResults() { return approvalResults; }
    public String getFinalStatus() { return finalStatus; }
    public void setFinalStatus(String finalStatus) { this.finalStatus = finalStatus; }
    public LocalDate getStatusDate() { return statusDate; }
    public void setStatusDate(LocalDate statusDate) { this.statusDate = statusDate; }
    public LocalDate getExtendedDate() { return extendedDate; }
    public void setExtendedDate(LocalDate extendedDate) { this.extendedDate = extendedDate; }

    public void addApprovalResult(String role, String decision) {
        this.approvalResults.add(role + ":" + decision);
    }

    public String getNextRequiredApprover() {
        if (approvalResults.size() < approvalChain.size()) {
            return approvalChain.get(approvalResults.size());
        }
        return null;
    }

    public boolean isFullyApproved() {
        if (approvalResults.size() != approvalChain.size()) return false;
        for (String res : approvalResults) {
            if (!res.endsWith(":APPROVE") && !res.endsWith(":APPROVED")) {
                return false;
            }
        }
        return true;
    }

    public String toCsvRow() {
        String chainStr = String.join("->", approvalChain);
        String resultsStr = approvalResults.isEmpty() ? "NONE" : String.join(";", approvalResults);
        return String.format("%s,%s,%s,%d,%d,%s,%d,%s,%s,%s,%s",
                candId, reqId, draftDate, offerCtc, joiningBonus,
                outOfBand ? "TRUE" : "FALSE", totalCost,
                chainStr, resultsStr, finalStatus, statusDate);
    }
}
