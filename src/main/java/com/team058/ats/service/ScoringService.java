package com.team058.ats.service;

public class ScoringService {

    public static final double L1_MIN_SCORE = 3.0;
    public static final double L2_MIN_SCORE = 3.5;
    public static final double HR_MIN_SCORE = 2.0;

    public static class ScoreEvaluation {
        public final boolean passed;
        public final String reasonCode;

        public ScoreEvaluation(boolean passed, String reasonCode) {
            this.passed = passed;
            this.reasonCode = reasonCode;
        }
    }

    public static ScoreEvaluation evaluate(String stage, double score) {
        if (score < 0.0 || score > 5.0) {
            return new ScoreEvaluation(false, "INVALID_SCORE_RANGE");
        }

        switch (stage.toUpperCase()) {
            case "L1":
                if (score >= L1_MIN_SCORE) {
                    return new ScoreEvaluation(true, "SUCCESS");
                } else {
                    return new ScoreEvaluation(false, score < 2.0 ? "SCORE_BELOW_2" : "SCORE_BELOW_MINIMUM");
                }
            case "L2":
                if (score >= L2_MIN_SCORE) {
                    return new ScoreEvaluation(true, "SUCCESS");
                } else {
                    return new ScoreEvaluation(false, score < 2.0 ? "SCORE_BELOW_2" : "SCORE_BELOW_MINIMUM");
                }
            case "HR":
                if (score >= HR_MIN_SCORE) {
                    return new ScoreEvaluation(true, "SUCCESS");
                } else {
                    return new ScoreEvaluation(false, "SCORE_BELOW_MINIMUM");
                }
            default:
                return new ScoreEvaluation(false, "UNKNOWN_STAGE");
        }
    }
}
