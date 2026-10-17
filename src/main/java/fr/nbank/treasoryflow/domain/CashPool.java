package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.PoolType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_cash_pool")
public class CashPool {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 64)
    private String headerEntityId;

    @Column(nullable = false, length = 64)
    private String headerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PoolType type;

    @Column(nullable = false, length = 16)
    private String status;
}
