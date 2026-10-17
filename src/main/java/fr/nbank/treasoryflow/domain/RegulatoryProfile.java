package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.Jurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_regulatory_profile")
public class RegulatoryProfile {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Jurisdiction jurisdiction;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 500)
    private String disclaimer;
}
