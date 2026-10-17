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
@Table(name = "tf_channel")
public class ConnectivityChannel {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 32)
    private String protocol;

    @Column(nullable = false, length = 40)
    private String messageFormat;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(precision = 28, scale = 2)
    private BigDecimal minAmount;

    @Column(precision = 28, scale = 2)
    private BigDecimal maxAmount;

    @Column(length = 8)
    private String cutoff;

    @Column(nullable = false)
    private int settlementLagDays;

    @Column(nullable = false, length = 400)
    private String notice;
}
