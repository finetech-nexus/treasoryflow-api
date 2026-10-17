package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "tf_fx_rate")
public class FxRate {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 3)
    private String baseCurrency;

    @Column(nullable = false, length = 3)
    private String quoteCurrency;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false, length = 32)
    private String method;

    @Column(nullable = false)
    private Instant capturedAt;
}
