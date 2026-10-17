package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.DealStatus;
import fr.nbank.treasoryflow.domain.Codes.PaymentPurpose;
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
@Table(name = "tf_payment_order")
public class PaymentOrder {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String debitAccountId;

    @Column(nullable = false)
    private String beneficiaryName;

    @Column(length = 64)
    private String beneficiaryEntityId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate valueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DealStatus status;

    @Column(length = 500)
    private String statusReason;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 64)
    private String approvedBy;

    private Instant approvedAt;
}
