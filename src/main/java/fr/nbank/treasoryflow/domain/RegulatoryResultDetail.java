package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_result_detail")
public class RegulatoryResultDetail {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 64)
    private String component;

    @Column(nullable = false, length = 16)
    private String side;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal weight;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal contribution;

    @Column(length = 300)
    private String explanation;
}
