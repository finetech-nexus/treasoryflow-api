package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.PositionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tf_cash_position")
public class CashPosition {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate asOf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PositionKind kind;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 64)
    private String sourceSystem;

    @Column(length = 128)
    private String sourceReference;

    @Column(length = 64)
    private String intercompanyPairId;

    @Column(nullable = false)
    private Instant updatedAt;
}
