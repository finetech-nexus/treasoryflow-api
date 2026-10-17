package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.ApprovalStatus;
import fr.nbank.treasoryflow.domain.Codes.FormulaCode;
import fr.nbank.treasoryflow.domain.Codes.Jurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tf_rule_version")
public class RegulatoryRuleVersion {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Jurisdiction jurisdiction;

    @Column(nullable = false, length = 64)
    private String entityScope;

    @Column(nullable = false, length = 300)
    private String regulatoryReference;

    @Column(nullable = false, length = 64)
    private String ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FormulaCode formulaCode;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus approvalStatus;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(length = 64)
    private String approvedBy;

    @Column(length = 64)
    private String testReference;

    @Column(nullable = false)
    private Instant createdAt;
}
