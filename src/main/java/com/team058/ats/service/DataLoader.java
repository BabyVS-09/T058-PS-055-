package com.team058.ats.service;

import com.team058.ats.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class DataLoader {

    public static class LoadedData {
        public final Map<String, Requisition> requisitions = new LinkedHashMap<>();
        public final Map<String, Candidate> candidates = new LinkedHashMap<>();
        public final Map<String, Interviewer> interviewers = new LinkedHashMap<>();
        public final List<AvailabilityWindow> availabilities = new ArrayList<>();
        public final List<Holiday> holidays = new ArrayList<>();
        public int duplicateAvailabilityCount = 0;
    }

    public static LoadedData loadAll(File dataDir) throws IOException {
        LoadedData data = new LoadedData();

        // 1. holidays.csv
        File holidaysFile = new File(dataDir, "holidays.csv");
        if (holidaysFile.exists()) {
            loadHolidays(holidaysFile, data.holidays);
        }

        // 2. requisitions.csv
        File reqFile = new File(dataDir, "requisitions.csv");
        if (reqFile.exists()) {
            loadRequisitions(reqFile, data.requisitions);
        }

        // 3. interviewers.csv
        File intFile = new File(dataDir, "interviewers.csv");
        if (intFile.exists()) {
            loadInterviewers(intFile, data.interviewers);
        }

        // 4. candidates.csv
        File candFile = new File(dataDir, "candidates.csv");
        if (candFile.exists()) {
            loadCandidates(candFile, data.candidates);
        }

        // 5. availability.csv (with deduplication)
        File availFile = new File(dataDir, "availability.csv");
        if (availFile.exists()) {
            data.duplicateAvailabilityCount = loadAvailability(availFile, data.availabilities);
        }

        return data;
    }

    private static void loadHolidays(File file, List<Holiday> list) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 2) {
                    LocalDate dt = LocalDate.parse(parts[0].trim());
                    String desc = parts[1].trim();
                    list.add(new Holiday(dt, desc));
                }
            }
        }
    }

    private static void loadRequisitions(File file, Map<String, Requisition> map) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 10) {
                    String reqId = parts[0].trim();
                    String title = parts[1].trim();
                    String department = parts[2].trim();
                    String grade = parts[3].trim();
                    long minCtc = Long.parseLong(parts[4].trim());
                    long maxCtc = Long.parseLong(parts[5].trim());
                    int headcount = Integer.parseInt(parts[6].trim());
                    String hmId = parts[7].trim();
                    String hrLeadId = parts[8].trim();
                    LocalDate openDate = LocalDate.parse(parts[9].trim());

                    Requisition req = new Requisition(reqId, title, department, grade,
                            minCtc, maxCtc, headcount, hmId, hrLeadId, openDate);
                    map.put(reqId, req);
                }
            }
        }
    }

    private static void loadInterviewers(File file, Map<String, Interviewer> map) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 6) {
                    String id = parts[0].trim();
                    String name = parts[1].trim();
                    String email = parts[2].trim();
                    String role = parts[3].trim();
                    String grade = parts[4].trim();
                    String department = parts[5].trim();
                    Interviewer intv = new Interviewer(id, name, email, role, grade, department);
                    map.put(id, intv);
                }
            }
        }
    }

    private static void loadCandidates(File file, Map<String, Candidate> map) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 10) {
                    String candId = parts[0].trim();
                    String name = parts[1].trim();
                    String email = parts[2].trim();
                    String phone = parts[3].trim();
                    long currentCtc = Long.parseLong(parts[4].trim());
                    long expectedCtc = Long.parseLong(parts[5].trim());
                    int noticePeriod = Integer.parseInt(parts[6].trim());
                    String source = parts[7].trim();
                    String reqId = parts[8].trim();
                    LocalDate applyDate = LocalDate.parse(parts[9].trim());

                    Candidate c = new Candidate(candId, name, email, phone, currentCtc,
                            expectedCtc, noticePeriod, source, reqId, applyDate);
                    map.put(candId, c);
                }
            }
        }
    }

    private static int loadAvailability(File file, List<AvailabilityWindow> list) throws IOException {
        Set<AvailabilityWindow> deduplicated = new LinkedHashSet<>();
        int duplicates = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 4) {
                    String intId = parts[0].trim();
                    LocalDate dt = LocalDate.parse(parts[1].trim());
                    LocalTime start = LocalTime.parse(parts[2].trim());
                    LocalTime end = LocalTime.parse(parts[3].trim());

                    AvailabilityWindow window = new AvailabilityWindow(intId, dt, start, end);
                    if (!deduplicated.add(window)) {
                        duplicates++;
                    }
                }
            }
        }
        list.addAll(deduplicated);
        Collections.sort(list);
        return duplicates;
    }
}
