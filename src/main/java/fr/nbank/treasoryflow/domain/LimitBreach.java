package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.BreachStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_limit_breach")
public class LimitBreach {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String limitId;

    @Column(length = 64)
    private String dealId;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal exposure;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal limitAmount;

    @Column(nullable = false, length = 64)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BreachStatus status;

    @Column(length = 64)
    private String waivedBy;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant resolvedAt;

    @Column(length = 300)
    private String reason;
}
