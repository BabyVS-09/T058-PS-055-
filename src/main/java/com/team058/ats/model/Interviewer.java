package com.team058.ats.model;

import java.util.Locale;

public class Interviewer {
    private String interviewerId;
    private String name;
    private String email;
    private String role;
    private String grade;
    private String department;

    public Interviewer(String interviewerId, String name, String email, String role, String grade, String department) {
        this.interviewerId = interviewerId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.grade = grade;
        this.department = department;
    }

    public String getInterviewerId() { return interviewerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getGrade() { return grade; }
    public String getDepartment() { return department; }

    public boolean canConductL2() {
        String g = grade.toUpperCase(Locale.ROOT);
        // L2 panels require senior interviewers (L4, L5, L6, SENIOR, PRINCIPAL, LEAD)
        if (g.contains("L3") || g.contains("JUNIOR") || g.contains("ASSOCIATE")) {
            return false;
        }
        return true;
    }
}
