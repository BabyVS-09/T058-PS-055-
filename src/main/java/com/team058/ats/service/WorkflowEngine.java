package com.team058.ats.service;

import com.team058.ats.model.*;

import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class WorkflowEngine {
    private final Map<String, Requisition> requisitions;
    private final Map<String, Candidate> candidates;
    private final Map<String, Interviewer> interviewers;
    private final List<AvailabilityWindow> availabilities;
    private final WorkingCalendar calendar;
    private final SchedulingService schedulingService;

    private final List<TransitionRecord> transitions = new ArrayList<>();
    private final List<InterviewRecord> interviewRecords = new ArrayList<>();
    private final Map<String, OfferRecord> offers = new LinkedHashMap<>();
    private final List<PipelineReportRecord> pipelineReports = new ArrayList<>();
    private final Set<String> appliedPairs = new HashSet<>();

    public WorkflowEngine(DataLoader.LoadedData data) {
        this.requisitions = data.requisitions;
        this.candidates = data.candidates;
        this.interviewers = data.interviewers;
        this.availabilities = data.availabilities;
        this.calendar = new WorkingCalendar(data.holidays);
        this.schedulingService = new SchedulingService(calendar, interviewers, availabilities);
    }

    public List<TransitionRecord> getTransitions() { return transitions; }
    public List<InterviewRecord> getInterviewRecords() { return interviewRecords; }
    public Collection<OfferRecord> getOffers() { return offers.values(); }
    public List<PipelineReportRecord> getPipelineReports() { return pipelineReports; }
    public Map<String, Requisition> getRequisitions() { return requisitions; }
    public Map<String, Candidate> getCandidates() { return candidates; }

    public int getFreeHeadcount(String reqId) {
        Requisition req = requisitions.get(reqId);
        if (req == null) return 0;
        int taken = 0;
        for (Candidate c : candidates.values()) {
            if (reqId.equals(c.getAppliedReqId())) {
                CandidateState s = c.getCurrentState();
                if (s == CandidateState.OFFER_EXTENDED ||
                    s == CandidateState.ACCEPTED ||
                    s == CandidateState.JOINED) {
                    taken++;
                }
            }
        }
        return Math.max(0, req.getHeadcount() - taken);
    }

    public void processCommands(File commandsFile) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(commandsFile))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                processCommandLine(lineNo, line);
            }
        }
    }

    public void processCommandLine(int lineNo, String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) return;

        String cmd = parts[0].toUpperCase();
        LocalDate date = LocalDate.parse(parts[1]);

        switch (cmd) {
            case "APPLY":
                handleApply(lineNo, date, parts);
                break;
            case "SCREEN":
                handleScreen(lineNo, date, parts);
                break;
            case "SCHEDULE":
                handleSchedule(lineNo, date, parts);
                break;
            case "SCORE":
                handleScore(lineNo, date, parts);
                break;
            case "BUYOUT":
                handleBuyout(lineNo, date, parts);
                break;
            case "DRAFT_OFFER":
                handleDraftOffer(lineNo, date, parts);
                break;
            case "APPROVE":
                handleApprove(lineNo, date, parts);
                break;
            case "EXTEND_OFFER":
                handleExtendOffer(lineNo, date, parts);
                break;
            case "ACCEPT":
                handleAccept(lineNo, date, parts);
                break;
            case "DECLINE":
                handleDecline(lineNo, date, parts);
                break;
            case "JOIN":
                handleJoin(lineNo, date, parts);
                break;
            case "REPORT":
                handleReport(date);
                break;
            default:
                break;
        }
    }

    private void handleApply(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 4) return;
        String candId = parts[2];
        String reqId = parts[3];

        Candidate c = candidates.get(candId);
        Requisition r = requisitions.get(reqId);
        if (c == null || r == null) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "NONE", "REJECTED", "FAILURE", "INVALID_ENTITY"));
            return;
        }

        // Check duplicate application
        String pairKey = candId + "::" + reqId;
        if (appliedPairs.contains(pairKey)) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "DUPLICATE_APPLICATION"));
            return;
        }

        // Check closed requisition
        if (r.isClosed() || (getFreeHeadcount(reqId) == 0 && countActiveHired(reqId) >= r.getHeadcount())) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "NONE", "REJECTED", "FAILURE", "CLOSED_REQUISITION"));
            return;
        }

        appliedPairs.add(pairKey);
        c.setAppliedReqId(reqId);
        c.setCurrentState(CandidateState.APPLIED);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "NONE", "APPLIED", "SUCCESS", "SUCCESS"));
    }

    private int countActiveHired(String reqId) {
        int count = 0;
        for (Candidate c : candidates.values()) {
            if (reqId.equals(c.getAppliedReqId()) && c.getCurrentState() == CandidateState.JOINED) {
                count++;
            }
        }
        return count;
    }

    private void handleScreen(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 4) return;
        String candId = parts[2];
        String result = parts[3].toUpperCase();

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();

        if (c.getCurrentState() != CandidateState.APPLIED) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        if ("PASS".equals(result)) {
            c.setCurrentState(CandidateState.SCREENED);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "APPLIED", "SCREENED", "SUCCESS", "SUCCESS"));
        } else {
            c.setCurrentState(CandidateState.REJECTED);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "APPLIED", "REJECTED", "SUCCESS", "SCREEN_FAILED"));
        }
    }

    private void handleSchedule(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 5) return;
        String candId = parts[2];
        String stage = parts[3].toUpperCase();
        String interviewerId = parts[4];

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();

        // Validate state transitions
        CandidateState expectedPrev = null;
        CandidateState nextState = null;
        if ("L1".equals(stage)) {
            expectedPrev = CandidateState.SCREENED;
            nextState = CandidateState.L1_SCHEDULED;
        } else if ("L2".equals(stage)) {
            expectedPrev = CandidateState.L1_PASSED;
            nextState = CandidateState.L2_SCHEDULED;
        } else if ("HR".equals(stage)) {
            expectedPrev = CandidateState.L2_PASSED;
            nextState = CandidateState.HR_SCHEDULED;
        } else {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "INVALID_STAGE"));
            return;
        }

        if (c.getCurrentState() != expectedPrev) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        // Call SchedulingService
        SchedulingService.ScheduleResult schedResult = schedulingService.scheduleInterview(candId, stage, interviewerId, date, 60);
        if (!schedResult.success) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", schedResult.reasonCode));
            return;
        }

        interviewRecords.add(new InterviewRecord(candId, stage, schedResult.scheduledDate, schedResult.startTime, schedResult.endTime, interviewerId, "SCHEDULED"));
        c.setCurrentState(nextState);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, expectedPrev.name(), nextState.name(), "SUCCESS", "SUCCESS"));
    }

    private void handleScore(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 5) return;
        String candId = parts[2];
        String stage = parts[3].toUpperCase();
        double score = Double.parseDouble(parts[4]);

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();

        CandidateState expectedCurrent = null;
        CandidateState passedState = null;
        if ("L1".equals(stage)) {
            expectedCurrent = CandidateState.L1_SCHEDULED;
            passedState = CandidateState.L1_PASSED;
        } else if ("L2".equals(stage)) {
            expectedCurrent = CandidateState.L2_SCHEDULED;
            passedState = CandidateState.L2_PASSED;
        } else if ("HR".equals(stage)) {
            expectedCurrent = CandidateState.HR_SCHEDULED;
            passedState = CandidateState.HR_PASSED;
        }

        if (c.getCurrentState() != expectedCurrent) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        ScoringService.ScoreEvaluation eval = ScoringService.evaluate(stage, score);

        // Update latest interview record result
        for (int i = interviewRecords.size() - 1; i >= 0; i--) {
            InterviewRecord ir = interviewRecords.get(i);
            if (ir.getCandId().equals(candId) && ir.getStage().equalsIgnoreCase(stage)) {
                ir.setResult(eval.passed ? "PASSED" : "FAILED");
                break;
            }
        }

        if (eval.passed) {
            c.setCurrentState(passedState);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, expectedCurrent.name(), passedState.name(), "SUCCESS", "SUCCESS"));
        } else {
            c.setCurrentState(CandidateState.REJECTED);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, expectedCurrent.name(), "REJECTED", "SUCCESS", eval.reasonCode));
        }
    }

    private void handleBuyout(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 4) return;
        String candId = parts[2];
        long amount = Long.parseLong(parts[3]);

        Candidate c = candidates.get(candId);
        if (c != null) {
            c.setBuyoutAmount(amount);
            transitions.add(new TransitionRecord(lineNo, date, candId, c.getAppliedReqId(), c.getCurrentState().name(), c.getCurrentState().name(), "SUCCESS", "BUYOUT_RECORDED"));
        }
    }

    private void handleDraftOffer(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 6) return;
        String candId = parts[2];
        long offerCtc = Long.parseLong(parts[3]);
        long joiningBonus = Long.parseLong(parts[4]);
        long buyout = Long.parseLong(parts[5]);

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();
        Requisition req = requisitions.get(reqId);

        if (c.getCurrentState() != CandidateState.HR_PASSED && c.getCurrentState() != CandidateState.WAITLIST) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        // EC7: Headcount contention and WAITLIST
        int freeHeadcount = getFreeHeadcount(reqId);
        if (freeHeadcount <= 0) {
            c.setCurrentState(CandidateState.WAITLIST);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "HR_PASSED", "WAITLIST", "SUCCESS", "HEADCOUNT_FULL_WAITLIST"));
            return;
        }

        OfferApprovalMatrix.OfferDraftResult draftResult = OfferApprovalMatrix.draftOffer(c, req, date, offerCtc, joiningBonus, buyout);
        offers.put(candId, draftResult.offerRecord);
        c.setCurrentState(CandidateState.OFFER_DRAFT);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "HR_PASSED", "OFFER_DRAFT", "SUCCESS", "SUCCESS"));
    }

    private void handleApprove(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 5) return;
        String candId = parts[2];
        String role = parts[3].toUpperCase();
        String decision = parts[4].toUpperCase();

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();
        OfferRecord offer = offers.get(candId);

        if (offer == null || (c.getCurrentState() != CandidateState.OFFER_DRAFT && c.getCurrentState() != CandidateState.OFFER_PENDING_APPROVAL)) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        OfferApprovalMatrix.ApprovalResult appResult = OfferApprovalMatrix.processApproval(offer, role, decision, date, calendar);
        if (!appResult.success) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", appResult.reasonCode));
            return;
        }

        if (appResult.rejected) {
            c.setCurrentState(CandidateState.REJECTED);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_DRAFT", "REJECTED", "SUCCESS", appResult.reasonCode));
        } else if (appResult.fullyApproved) {
            c.setCurrentState(CandidateState.OFFER_APPROVED);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_DRAFT", "OFFER_APPROVED", "SUCCESS", "SUCCESS"));
        } else {
            c.setCurrentState(CandidateState.OFFER_PENDING_APPROVAL);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_DRAFT", "OFFER_PENDING_APPROVAL", "SUCCESS", "SUCCESS"));
        }
    }

    private void handleExtendOffer(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 3) return;
        String candId = parts[2];

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();
        OfferRecord offer = offers.get(candId);

        if (c.getCurrentState() != CandidateState.OFFER_APPROVED || offer == null || !"APPROVED".equals(offer.getFinalStatus())) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        offer.setExtendedDate(date);
        offer.setFinalStatus("EXTENDED");
        offer.setStatusDate(date);
        c.setCurrentState(CandidateState.OFFER_EXTENDED);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_APPROVED", "OFFER_EXTENDED", "SUCCESS", "SUCCESS"));
    }

    private void handleAccept(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 3) return;
        String candId = parts[2];

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();
        OfferRecord offer = offers.get(candId);

        if (c.getCurrentState() != CandidateState.OFFER_EXTENDED || offer == null) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        // EC5: Acceptance on day 7 vs day 8
        if (!OfferApprovalMatrix.isOfferAcceptanceValid(offer, date)) {
            offer.setFinalStatus("EXPIRED");
            offer.setStatusDate(date);
            c.setCurrentState(CandidateState.REJECTED);
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_EXTENDED", "REJECTED", "FAILURE", "OFFER_EXPIRED"));
            return;
        }

        offer.setFinalStatus("ACCEPTED");
        offer.setStatusDate(date);
        c.setCurrentState(CandidateState.ACCEPTED);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_EXTENDED", "ACCEPTED", "SUCCESS", "SUCCESS"));
    }

    private void handleDecline(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 3) return;
        String candId = parts[2];

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();
        OfferRecord offer = offers.get(candId);

        if (c.getCurrentState() != CandidateState.OFFER_EXTENDED && c.getCurrentState() != CandidateState.OFFER_APPROVED) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        if (offer != null) {
            offer.setFinalStatus("DECLINED");
            offer.setStatusDate(date);
        }
        c.setCurrentState(CandidateState.DECLINED);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "OFFER_EXTENDED", "DECLINED", "SUCCESS", "CANDIDATE_DECLINED"));
    }

    private void handleJoin(int lineNo, LocalDate date, String[] parts) {
        if (parts.length < 3) return;
        String candId = parts[2];

        Candidate c = candidates.get(candId);
        if (c == null) return;
        String reqId = c.getAppliedReqId();
        OfferRecord offer = offers.get(candId);

        if (c.getCurrentState() != CandidateState.ACCEPTED) {
            transitions.add(new TransitionRecord(lineNo, date, candId, reqId, c.getCurrentState().name(), c.getCurrentState().name(), "FAILURE", "ILLEGAL_TRANSITION"));
            return;
        }

        if (offer != null) {
            offer.setFinalStatus("JOINED");
            offer.setStatusDate(date);
        }
        c.setCurrentState(CandidateState.JOINED);
        transitions.add(new TransitionRecord(lineNo, date, candId, reqId, "ACCEPTED", "JOINED", "SUCCESS", "SUCCESS"));

        // If requisition headcount is completely filled, mark req closed
        Requisition req = requisitions.get(reqId);
        if (req != null && countActiveHired(reqId) >= req.getHeadcount()) {
            req.setClosed(true);
        }
    }

    private void handleReport(LocalDate reportDate) {
        for (Requisition req : requisitions.values()) {
            String reqId = req.getReqId();
            long ageDays = ChronoUnit.DAYS.between(req.getOpenDate(), reportDate);

            int screened = 0;
            int l1 = 0;
            int l2 = 0;
            int hr = 0;
            int offerDraft = 0;
            int offerApproved = 0;
            int offered = 0;
            int accepted = 0;
            int joined = 0;
            int waitlist = 0;
            boolean noticeRisk = false;
            Set<String> pendingApprovers = new LinkedHashSet<>();

            for (Candidate c : candidates.values()) {
                if (reqId.equals(c.getAppliedReqId())) {
                    CandidateState s = c.getCurrentState();
                    if (s == CandidateState.SCREENED) screened++;
                    else if (s == CandidateState.L1_SCHEDULED || s == CandidateState.L1_PASSED) l1++;
                    else if (s == CandidateState.L2_SCHEDULED || s == CandidateState.L2_PASSED) l2++;
                    else if (s == CandidateState.HR_SCHEDULED || s == CandidateState.HR_PASSED) hr++;
                    else if (s == CandidateState.OFFER_DRAFT) offerDraft++;
                    else if (s == CandidateState.OFFER_PENDING_APPROVAL) {
                        offerDraft++;
                        OfferRecord o = offers.get(c.getCandId());
                        if (o != null && o.getNextRequiredApprover() != null) {
                            pendingApprovers.add(o.getNextRequiredApprover());
                        }
                    }
                    else if (s == CandidateState.OFFER_APPROVED) offerApproved++;
                    else if (s == CandidateState.OFFER_EXTENDED) offered++;
                    else if (s == CandidateState.ACCEPTED) accepted++;
                    else if (s == CandidateState.JOINED) joined++;
                    else if (s == CandidateState.WAITLIST) waitlist++;

                    // Notice period risk
                    if (c.getNoticePeriodDays() > 60 && s != CandidateState.REJECTED && s != CandidateState.JOINED) {
                        noticeRisk = true;
                    }
                }
            }

            int freeHeadcount = getFreeHeadcount(reqId);
            boolean atRisk = (ageDays > 45 && freeHeadcount > 0) || noticeRisk;
            String pendingStr = pendingApprovers.isEmpty() ? "NONE" : String.join(";", pendingApprovers);

            pipelineReports.add(new PipelineReportRecord(
                    reportDate, reqId, ageDays, atRisk, freeHeadcount,
                    screened, l1, l2, hr, offerDraft, offerApproved,
                    offered, accepted, joined, waitlist, pendingStr
            ));
        }
    }

    public void writeOutputCsvFiles(File outputDir) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 1. transitions.csv
        File transFile = new File(outputDir, "transitions.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(transFile))) {
            pw.println("line_no,date,cand_id,req_id,from_state,to_state,result,reason_code");
            for (TransitionRecord tr : transitions) {
                pw.println(tr.toCsvRow());
            }
        }

        // 2. interviews.csv
        File intFile = new File(outputDir, "interviews.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(intFile))) {
            pw.println("cand_id,stage,scheduled_date,start_time,end_time,panel,result");
            for (InterviewRecord ir : interviewRecords) {
                pw.println(ir.toCsvRow());
            }
        }

        // 3. offers.csv
        File offersFile = new File(outputDir, "offers.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(offersFile))) {
            pw.println("cand_id,req_id,draft_date,offer_ctc,joining_bonus,out_of_band,total_cost,approval_chain,approval_results,final_status,status_date");
            for (OfferRecord o : offers.values()) {
                pw.println(o.toCsvRow());
            }
        }

        // 4. pipeline_report.csv
        File reportFile = new File(outputDir, "pipeline_report.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(reportFile))) {
            pw.println("report_date,req_id,age_days,at_risk,free_headcount,screened,l1,l2,hr,offer_draft,offer_approved,offered,accepted,joined,waitlist,offers_pending_with");
            for (PipelineReportRecord pr : pipelineReports) {
                pw.println(pr.toCsvRow());
            }
        }
    }
}
