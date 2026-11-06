package com.team058.ats.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class AvailabilityWindow implements Comparable<AvailabilityWindow> {
    private String interviewerId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;

    public AvailabilityWindow(String interviewerId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.interviewerId = interviewerId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getInterviewerId() { return interviewerId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AvailabilityWindow)) return false;
        AvailabilityWindow that = (AvailabilityWindow) o;
        return Objects.equals(interviewerId, that.interviewerId) &&
               Objects.equals(date, that.date) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interviewerId, date, startTime, endTime);
    }

    @Override
    public int compareTo(AvailabilityWindow o) {
        int c = this.date.compareTo(o.date);
        if (c != 0) return c;
        c = this.startTime.compareTo(o.startTime);
        if (c != 0) return c;
        c = this.endTime.compareTo(o.endTime);
        if (c != 0) return c;
        return this.interviewerId.compareTo(o.interviewerId);
    }
}
