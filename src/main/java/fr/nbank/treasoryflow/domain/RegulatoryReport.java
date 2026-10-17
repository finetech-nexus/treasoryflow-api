package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_regulatory_report")
public class RegulatoryReport {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String reportCode;

    @Column(nullable = false)
    private LocalDate referenceDate;

    @Column(nullable = false, length = 64)
    private String calculationRunId;

    @Column(nullable = false, length = 64)
    private String ruleVersionId;

    @Column(nullable = false, length = 64)
    private String inputSnapshotHash;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 64)
    private String validatedBy;

    private Instant validatedAt;
}
