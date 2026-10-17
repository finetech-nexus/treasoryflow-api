package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_calculation_run")
public class RegulatoryCalculationRun {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String ruleVersionId;

    @Column(nullable = false, length = 64)
    private String ruleId;

    @Column(nullable = false, length = 64)
    private String ruleVersion;

    @Column(nullable = false, length = 32)
    private String formulaCode;

    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false)
    private LocalDate referenceDate;

    @Column(nullable = false, precision = 28, scale = 6)
    private BigDecimal result;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(nullable = false, precision = 28, scale = 6)
    private BigDecimal threshold;

    @Column(nullable = false)
    private boolean thresholdMet;

    @Column(nullable = false, length = 64)
    private String inputSnapshotHash;

    @Column(nullable = false, columnDefinition = "text")
    private String inputSnapshot;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant executedAt;

    @Column(nullable = false, length = 64)
    private String executedBy;

    @Column(length = 64)
    private String reportId;
}
