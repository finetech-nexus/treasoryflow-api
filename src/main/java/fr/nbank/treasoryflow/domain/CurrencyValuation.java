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
@Table(name = "tf_currency_valuation")
public class CurrencyValuation {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String contextType;

    @Column(nullable = false, length = 64)
    private String contextId;

    @Column(nullable = false, length = 3)
    private String originalCurrency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal originalAmount;

    @Column(nullable = false, length = 3)
    private String reportingCurrency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal reportingAmount;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false, length = 64)
    private String rateSource;

    @Column(nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false, length = 32)
    private String method;
}
