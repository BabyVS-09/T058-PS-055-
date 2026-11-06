package com.team058.ats.model;

import java.time.LocalDate;

public class Holiday {
    private LocalDate holidayDate;
    private String description;

    public Holiday(LocalDate holidayDate, String description) {
        this.holidayDate = holidayDate;
        this.description = description;
    }

    public LocalDate getHolidayDate() { return holidayDate; }
    public String getDescription() { return description; }
}
