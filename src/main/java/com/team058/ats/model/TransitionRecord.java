package com.team058.ats.model;

import java.time.LocalDate;

public class TransitionRecord {
    private int lineNo;
    private LocalDate date;
    private String candId;
    private String reqId;
    private String fromState;
    private String toState;
    private String result;
    private String reasonCode;

    public TransitionRecord(int lineNo, LocalDate date, String candId, String reqId,
                            String fromState, String toState, String result, String reasonCode) {
        this.lineNo = lineNo;
        this.date = date;
        this.candId = candId;
        this.reqId = reqId;
        this.fromState = fromState;
        this.toState = toState;
        this.result = result;
        this.reasonCode = reasonCode;
    }

    public int getLineNo() { return lineNo; }
    public LocalDate getDate() { return date; }
    public String getCandId() { return candId; }
    public String getReqId() { return reqId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getResult() { return result; }
    public String getReasonCode() { return reasonCode; }

    public String toCsvRow() {
        return String.format("%d,%s,%s,%s,%s,%s,%s,%s",
                lineNo, date, candId, reqId, fromState, toState, result, reasonCode);
    }
}
