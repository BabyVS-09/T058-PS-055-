package com.team058.ats.service;

import com.team058.ats.model.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class SchedulingService {
    private final WorkingCalendar calendar;
    private final Map<String, Interviewer> interviewers;
    private final List<AvailabilityWindow> availabilities;
    private final List<InterviewRecord> bookedInterviews = new ArrayList<>();

    public SchedulingService(WorkingCalendar calendar,
                             Map<String, Interviewer> interviewers,
                             List<AvailabilityWindow> availabilities) {
        this.calendar = calendar;
        this.interviewers = interviewers;
        this.availabilities = new ArrayList<>(availabilities);
    }

    public List<InterviewRecord> getBookedInterviews() {
        return Collections.unmodifiableList(bookedInterviews);
    }

    public static class ScheduleResult {
        public final boolean success;
        public final LocalDate scheduledDate;
        public final LocalTime startTime;
        public final LocalTime endTime;
        public final String reasonCode;

        public ScheduleResult(boolean success, LocalDate scheduledDate, LocalTime startTime, LocalTime endTime, String reasonCode) {
            this.success = success;
            this.scheduledDate = scheduledDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.reasonCode = reasonCode;
        }

        public static ScheduleResult fail(String reasonCode) {
            return new ScheduleResult(false, null, null, null, reasonCode);
        }

        public static ScheduleResult ok(LocalDate date, LocalTime start, LocalTime end) {
            return new ScheduleResult(true, date, start, end, "SUCCESS");
        }
    }

    public ScheduleResult scheduleInterview(String candId, String stage, String interviewerId,
                                            LocalDate commandDate, int durationMinutes) {
        Interviewer interviewer = interviewers.get(interviewerId);
        if (interviewer == null) {
            return ScheduleResult.fail("INVALID_INTERVIEWER");
        }

        // Validate interviewer grade for L2 panels
        if ("L2".equalsIgnoreCase(stage) && !interviewer.canConductL2()) {
            return ScheduleResult.fail("INVALID_PANEL");
        }

        // Search within the next 10 working days starting from commandDate (or next working day if commandDate is not working day)
        LocalDate searchStart = calendar.isWorkingDay(commandDate) ? commandDate : calendar.getNextWorkingDay(commandDate);
        LocalDate searchLimit = calendar.addWorkingDays(searchStart, 10);

        // Filter availability windows for this interviewer
        List<AvailabilityWindow> windows = new ArrayList<>();
        for (AvailabilityWindow w : availabilities) {
            if (w.getInterviewerId().equals(interviewerId) &&
                !w.getDate().isBefore(searchStart) &&
                !w.getDate().isAfter(searchLimit) &&
                calendar.isWorkingDay(w.getDate())) {
                windows.add(w);
            }
        }
        Collections.sort(windows);

        for (AvailabilityWindow w : windows) {
            LocalDate dt = w.getDate();
            LocalTime windowStart = w.getStartTime().isBefore(WorkingCalendar.WORK_START) ? WorkingCalendar.WORK_START : w.getStartTime();
            LocalTime windowEnd = w.getEndTime().isAfter(WorkingCalendar.WORK_END) ? WorkingCalendar.WORK_END : w.getEndTime();

            LocalTime candidateStart = windowStart;
            while (!candidateStart.plusMinutes(durationMinutes).isAfter(windowEnd)) {
                LocalTime candidateEnd = candidateStart.plusMinutes(durationMinutes);

                // Check conflicts and 15-minute gap with other interviews for this interviewer
                if (isInterviewerAvailable(interviewerId, dt, candidateStart, candidateEnd)) {
                    // Book this slot!
                    InterviewRecord record = new InterviewRecord(candId, stage, dt, candidateStart, candidateEnd, interviewerId, "SCHEDULED");
                    bookedInterviews.add(record);
                    return ScheduleResult.ok(dt, candidateStart, candidateEnd);
                }

                // Advance candidateStart in 15-minute increments
                candidateStart = candidateStart.plusMinutes(15);
            }
        }

        return ScheduleResult.fail("NO_SLOT_AVAILABLE");
    }

    private boolean isInterviewerAvailable(String interviewerId, LocalDate date, LocalTime start, LocalTime end) {
        for (InterviewRecord b : bookedInterviews) {
            if (b.getPanel().equals(interviewerId) && b.getScheduledDate().equals(date)) {
                // Must maintain at least a 15-minute gap between interviews
                // Conflict if start < existingEnd + 15m AND end + 15m > existingStart
                LocalTime bufferedExistingStart = b.getStartTime().minusMinutes(15);
                LocalTime bufferedExistingEnd = b.getEndTime().plusMinutes(15);

                if (start.isBefore(bufferedExistingEnd) && end.isAfter(bufferedExistingStart)) {
                    return false;
                }
            }
        }
        return true;
    }
}
