package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_compliance_approval")
public class ComplianceApproval {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String subjectType;

    @Column(nullable = false, length = 64)
    private String subjectId;

    @Column(nullable = false, length = 64)
    private String approver;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(nullable = false)
    private Instant decidedAt;

    @Column(length = 300)
    private String comment;
}
