package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "tf_intercompany_flow")
public class IntercompanyFlow {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String pairId;

    @Column(nullable = false, length = 64)
    private String payerEntityId;

    @Column(nullable = false, length = 64)
    private String receiverEntityId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate valueDate;

    @Column(nullable = false)
    private boolean eliminateAccounting;

    @Column(nullable = false)
    private boolean eliminatePrudential;

    @Column(nullable = false, length = 64)
    private String assetPositionId;

    @Column(nullable = false, length = 64)
    private String liabilityPositionId;
}
