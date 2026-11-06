package com.team058.ats.service;

import com.team058.ats.model.Holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class WorkingCalendar {
    private final Set<LocalDate> holidays = new HashSet<>();
    public static final LocalTime WORK_START = LocalTime.of(9, 0);
    public static final LocalTime WORK_END = LocalTime.of(18, 0);

    public WorkingCalendar(Collection<Holiday> holidayList) {
        if (holidayList != null) {
            for (Holiday h : holidayList) {
                holidays.add(h.getHolidayDate());
            }
        }
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    public boolean isWorkingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }

    public LocalDate getNextWorkingDay(LocalDate date) {
        LocalDate current = date.plusDays(1);
        while (!isWorkingDay(current)) {
            current = current.plusDays(1);
        }
        return current;
    }

    public LocalDate addWorkingDays(LocalDate startDate, int days) {
        LocalDate current = startDate;
        int added = 0;
        while (added < days) {
            current = current.plusDays(1);
            if (isWorkingDay(current)) {
                added++;
            }
        }
        return current;
    }

    public int countWorkingDaysBetween(LocalDate startInclusive, LocalDate endInclusive) {
        if (endInclusive.isBefore(startInclusive)) return 0;
        int count = 0;
        LocalDate d = startInclusive;
        while (!d.isAfter(endInclusive)) {
            if (isWorkingDay(d)) {
                count++;
            }
            d = d.plusDays(1);
        }
        return count;
    }
}
