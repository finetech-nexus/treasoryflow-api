package fr.nbank.treasoryflow.domain;

public final class Codes {
    private Codes() {}

    public enum Jurisdiction {
        TN, EU
    }

    public enum FormulaCode {
        TN_LIQUIDITY_STOCK(Jurisdiction.TN),
        EU_LCR(Jurisdiction.EU),
        EU_NSFR(Jurisdiction.EU);

        public final Jurisdiction jurisdiction;

        FormulaCode(Jurisdiction jurisdiction) {
            this.jurisdiction = jurisdiction;
        }
    }

    public enum ApprovalStatus {
        DRAFT, VALIDATED, PUBLISHED, RETIRED
    }

    public enum DealStatus {
        DRAFT, PENDING_VALIDATION, VALIDATED, TRANSMITTED, CONFIRMED, SETTLED, RECONCILED, REJECTED, CANCELLED
    }

    public enum PositionKind {
        BOOKED, AVAILABLE, CONSOLIDATED, FORECAST, SIMULATED
    }

    public enum FlowClass {
        CONTRACTUAL, ESTIMATED, SIMULATED
    }

    public enum ImportStatus {
        APPLIED, PARTIAL, REJECTED
    }

    public enum BreachStatus {
        OPEN, WAIVED, RESOLVED, BLOCKED
    }

    public enum LimitAction {
        WARN, BLOCK, WAIVER_REQUIRED
    }

    public enum RecommendationStatus {
        DRAFT, APPROVED, REJECTED
    }
}
