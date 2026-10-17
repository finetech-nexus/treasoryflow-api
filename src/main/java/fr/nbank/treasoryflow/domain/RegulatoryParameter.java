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
@Table(name = "tf_rule_parameter")
public class RegulatoryParameter {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String ruleVersionId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String component;

    @Column(name = "parameter_value", nullable = false, precision = 28, scale = 8)
    private BigDecimal value;

    @Column(nullable = false, length = 16)
    private String unit;
}
