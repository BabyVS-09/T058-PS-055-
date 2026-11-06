package com.team058.ats.model;

import java.time.LocalDate;

public class Candidate {
    private String candId;
    private String name;
    private String email;
    private String phone;
    private long currentCtc;
    private long expectedCtc;
    private int noticePeriodDays;
    private String source;
    private String appliedReqId;
    private LocalDate applyDate;
    private CandidateState currentState;
    private long buyoutAmount;

    public Candidate(String candId, String name, String email, String phone,
                     long currentCtc, long expectedCtc, int noticePeriodDays,
                     String source, String appliedReqId, LocalDate applyDate) {
        this.candId = candId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.currentCtc = currentCtc;
        this.expectedCtc = expectedCtc;
        this.noticePeriodDays = noticePeriodDays;
        this.source = source;
        this.appliedReqId = appliedReqId;
        this.applyDate = applyDate;
        this.currentState = CandidateState.APPLIED;
        this.buyoutAmount = 0;
    }

    public String getCandId() { return candId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public long getCurrentCtc() { return currentCtc; }
    public long getExpectedCtc() { return expectedCtc; }
    public int getNoticePeriodDays() { return noticePeriodDays; }
    public String getSource() { return source; }
    public String getAppliedReqId() { return appliedReqId; }
    public void setAppliedReqId(String appliedReqId) { this.appliedReqId = appliedReqId; }
    public LocalDate getApplyDate() { return applyDate; }
    public CandidateState getCurrentState() { return currentState; }
    public void setCurrentState(CandidateState currentState) { this.currentState = currentState; }
    public long getBuyoutAmount() { return buyoutAmount; }
    public void setBuyoutAmount(long buyoutAmount) { this.buyoutAmount = buyoutAmount; }
}
