package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.RecommendationStatus;
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
@Table(name = "tf_funding_recommendation")
public class FundingRecommendation {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate asOf;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal deficit;

    @Column(nullable = false, length = 32)
    private String suggestedProduct;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal suggestedAmount;

    @Column(length = 64)
    private String proposedSourceEntityId;

    @Column(length = 300)
    private String blockedSources;

    @Column(nullable = false)
    private boolean proposesRestrictedTransfer;

    @Column(nullable = false, length = 1000)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecommendationStatus status;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(length = 64)
    private String approvedBy;

    @Column(nullable = false)
    private Instant createdAt;
}
