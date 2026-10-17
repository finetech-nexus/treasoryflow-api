package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.LimitAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_exposure_limit")
public class ExposureLimit {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 32)
    private String dimension;

    @Column(nullable = false, length = 64)
    private String dimensionValue;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal limitAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LimitAction action;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;
}
