package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.DealStatus;
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
@Table(name = "tf_deal")
public class TreasuryDeal {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 32)
    private String productCode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 3)
    private String counterCurrency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal amount;

    @Column(precision = 28, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false, length = 64)
    private String counterpartyId;

    @Column(nullable = false, length = 64)
    private String reference;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private LocalDate valueDate;

    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DealStatus status;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(length = 64)
    private String approvedBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant approvedAt;

    @Column(length = 300)
    private String statusReason;
}
