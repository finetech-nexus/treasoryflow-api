package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.FlowClass;
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
@Table(name = "tf_forecast_flow")
public class ForecastFlow {
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
    private LocalDate valueDate;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlowClass flowClass;

    @Column(nullable = false, length = 32)
    private String scenarioCode;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean cancellable;

    @Column(length = 64)
    private String sourceReference;
}
