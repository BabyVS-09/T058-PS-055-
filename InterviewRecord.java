package com.team058.ats.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class InterviewRecord {
    private String candId;
    private String stage;
    private LocalDate scheduledDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String panel;
    private String result;

    public InterviewRecord(String candId, String stage, LocalDate scheduledDate,
                           LocalTime startTime, LocalTime endTime, String panel, String result) {
        this.candId = candId;
        this.stage = stage;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.panel = panel;
        this.result = result;
    }

    public String getCandId() { return candId; }
    public String getStage() { return stage; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getPanel() { return panel; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String toCsvRow() {
        return String.format("%s,%s,%s,%s,%s,%s,%s",
                candId, stage, scheduledDate, startTime, endTime, panel, result);
    }
}
