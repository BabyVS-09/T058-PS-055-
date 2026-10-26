package com.ats;

import com.ats.model.CandidateStage;
import com.ats.model.ReasonCode;
import com.ats.parser.CommandExecutionResult;
import com.ats.parser.CommandFileParser;
import com.ats.service.RecruitmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration and parser unit tests verifying {@link CommandFileParser} against
 * {@code sample-data/happy-path.txt} and malformed/negative command inputs (BR-38).
 */
class CommandFileParserTest {

    @Test
    @DisplayName("BR-38: Executes sample-data/happy-path.txt end-to-end with zero errors")
    void shouldProcessHappyPathCommandFileSuccessfully() throws IOException {
        RecruitmentService service = new RecruitmentService();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CommandFileParser parser = new CommandFileParser(service, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        List<CommandExecutionResult> results = parser.processFile(Path.of("sample-data", "happy-path.txt"));

        assertFalse(results.isEmpty(), "Expected commands to be parsed from sample-data/happy-path.txt");
        assertTrue(results.stream().allMatch(CommandExecutionResult::success),
                "All commands in happy-path.txt should succeed");

        assertEquals(2, service.getAllRequisitions().size());
        assertEquals(4, service.getAllCandidates().size());
        assertEquals(4, service.getAllApplications().size());

        assertEquals(CandidateStage.JOINED, service.getRequiredApplication("APP-301").getCurrentStage());
        assertEquals(CandidateStage.WAITLIST, service.getRequiredApplication("APP-302").getCurrentStage());
        assertEquals(CandidateStage.DECLINED, service.getRequiredApplication("APP-303").getCurrentStage());
        assertEquals(CandidateStage.OFFER_DRAFT, service.getRequiredApplication("APP-304").getCurrentStage());

        String consoleOutput = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(consoleOutput.contains("[SUCCESS]"));
        assertTrue(consoleOutput.contains("ATS PIPELINE SNAPSHOT"));
    }

    @Test
    @DisplayName("Parser Validation: Reports exact reason codes for missing fields, invalid CTCs, duplicates, and illegal transitions")
    void shouldReportReasonCodesForInvalidCommandLines() {
        RecruitmentService service = new RecruitmentService();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CommandFileParser parser = new CommandFileParser(service, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        List<String> commands = List.of(
                // 1. Valid requisition
                "REGISTER_REQUISITION | REQ-500 | Backend Developer | G3 | 2 | Bengaluru | 1000000 | 1500000 | Vikram Desai | 2026-03-01",
                // 2. Duplicate requisition ID
                "REGISTER_REQUISITION | REQ-500 | Backend Developer | G3 | 2 | Bengaluru | 1000000 | 1500000 | Vikram Desai | 2026-03-01",
                // 3. Invalid CTC (min > max)
                "REGISTER_REQUISITION | REQ-501 | Backend Developer | G3 | 2 | Bengaluru | 1800000 | 1500000 | Vikram Desai | 2026-03-01",
                // 4. Missing candidate name
                "REGISTER_CANDIDATE | CAN-500 |   | 30 | 900000 | 1200000 | 3.5 | PORTAL",
                // 5. Valid candidate & application
                "REGISTER_CANDIDATE | CAN-500 | Nisha Patel | 30 | 900000 | 1200000 | 3.5 | PORTAL",
                "APPLY | APP-500 | CAN-500 | REQ-500 | 2026-03-02",
                // 6. Illegal stage jump SCREENED -> OFFERED
                "TRANSITION_STAGE | APP-500 | OFFERED",
                // 7. Unknown command
                "DELETE_ALL | NOW"
        );

        List<CommandExecutionResult> results = parser.processLines(commands);

        assertEquals(8, results.size());
        assertEquals(ReasonCode.SUCCESS, results.get(0).reasonCode());
        assertEquals(ReasonCode.DUPLICATE_REQUISITION_ID, results.get(1).reasonCode());
        assertEquals(ReasonCode.INVALID_CTC, results.get(2).reasonCode());
        assertEquals(ReasonCode.MISSING_REQUIRED_FIELD, results.get(3).reasonCode());
        assertEquals(ReasonCode.SUCCESS, results.get(4).reasonCode());
        assertEquals(ReasonCode.SUCCESS, results.get(5).reasonCode());
        assertEquals(ReasonCode.ILLEGAL_STAGE_TRANSITION, results.get(6).reasonCode());
        assertEquals(ReasonCode.UNKNOWN_COMMAND, results.get(7).reasonCode());
    }
}
