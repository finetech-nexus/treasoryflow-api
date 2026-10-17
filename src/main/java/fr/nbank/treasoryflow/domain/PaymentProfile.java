package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_payment_profile")
public class PaymentProfile {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 24)
    private String purpose;

    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 16)
    private String rule;

    @Column(length = 32)
    private String channelCode;
}
