package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.Jurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_legal_entity")
public class LegalEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String legalName;

    @Column(nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Jurisdiction jurisdiction;

    @Column(nullable = false, length = 3)
    private String reportingCurrency;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;

    @Column(nullable = false, length = 64)
    private String regulatoryProfileId;

    @Column(nullable = false)
    private boolean transferRestricted;

    @Column(length = 500)
    private String transferRestrictionReason;
}
