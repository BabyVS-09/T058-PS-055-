package com.team058.ats.model;

import java.time.LocalDate;

public class PipelineReportRecord {
    private LocalDate reportDate;
    private String reqId;
    private long ageDays;
    private boolean atRisk;
    private int freeHeadcount;
    private int screened;
    private int l1;
    private int l2;
    private int hr;
    private int offerDraft;
    private int offerApproved;
    private int offered;
    private int accepted;
    private int joined;
    private int waitlist;
    private String offersPendingWith;

    public PipelineReportRecord(LocalDate reportDate, String reqId, long ageDays, boolean atRisk,
                                int freeHeadcount, int screened, int l1, int l2, int hr,
                                int offerDraft, int offerApproved, int offered, int accepted,
                                int joined, int waitlist, String offersPendingWith) {
        this.reportDate = reportDate;
        this.reqId = reqId;
        this.ageDays = ageDays;
        this.atRisk = atRisk;
        this.freeHeadcount = freeHeadcount;
        this.screened = screened;
        this.l1 = l1;
        this.l2 = l2;
        this.hr = hr;
        this.offerDraft = offerDraft;
        this.offerApproved = offerApproved;
        this.offered = offered;
        this.accepted = accepted;
        this.joined = joined;
        this.waitlist = waitlist;
        this.offersPendingWith = (offersPendingWith == null || offersPendingWith.trim().isEmpty()) ? "NONE" : offersPendingWith;
    }

    public LocalDate getReportDate() { return reportDate; }
    public String getReqId() { return reqId; }
    public long getAgeDays() { return ageDays; }
    public boolean isAtRisk() { return atRisk; }
    public int getFreeHeadcount() { return freeHeadcount; }
    public int getScreened() { return screened; }
    public int getL1() { return l1; }
    public int getL2() { return l2; }
    public int getHr() { return hr; }
    public int getOfferDraft() { return offerDraft; }
    public int getOfferApproved() { return offerApproved; }
    public int getOffered() { return offered; }
    public int getAccepted() { return accepted; }
    public int getJoined() { return joined; }
    public int getWaitlist() { return waitlist; }
    public String getOffersPendingWith() { return offersPendingWith; }

    public String toCsvRow() {
        return String.format("%s,%s,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s",
                reportDate, reqId, ageDays, atRisk ? "TRUE" : "FALSE", freeHeadcount,
                screened, l1, l2, hr, offerDraft, offerApproved, offered, accepted,
                joined, waitlist, offersPendingWith);
    }
}
