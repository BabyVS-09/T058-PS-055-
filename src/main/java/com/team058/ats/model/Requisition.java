package com.team058.ats.model;

import java.time.LocalDate;

public class Requisition {
    private String reqId;
    private String title;
    private String department;
    private String grade;
    private long minCtc;
    private long maxCtc;
    private int headcount;
    private String hiringManagerId;
    private String hrLeadId;
    private LocalDate openDate;
    private boolean isClosed;

    public Requisition(String reqId, String title, String department, String grade,
                       long minCtc, long maxCtc, int headcount,
                       String hiringManagerId, String hrLeadId, LocalDate openDate) {
        this.reqId = reqId;
        this.title = title;
        this.department = department;
        this.grade = grade;
        this.minCtc = minCtc;
        this.maxCtc = maxCtc;
        this.headcount = headcount;
        this.hiringManagerId = hiringManagerId;
        this.hrLeadId = hrLeadId;
        this.openDate = openDate;
        this.isClosed = false;
    }

    public String getReqId() { return reqId; }
    public String getTitle() { return title; }
    public String getDepartment() { return department; }
    public String getGrade() { return grade; }
    public long getMinCtc() { return minCtc; }
    public long getMaxCtc() { return maxCtc; }
    public int getHeadcount() { return headcount; }
    public String getHiringManagerId() { return hiringManagerId; }
    public String getHrLeadId() { return hrLeadId; }
    public LocalDate getOpenDate() { return openDate; }
    public boolean isClosed() { return isClosed; }
    public void setClosed(boolean closed) { isClosed = closed; }
}
