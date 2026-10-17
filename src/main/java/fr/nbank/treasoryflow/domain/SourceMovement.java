package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_source_movement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tf_movement", columnNames = {"source_system", "external_id"})
})
public class SourceMovement {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String batchId;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 28, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate valueDate;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false, length = 128)
    private String reference;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 300)
    private String rejectReason;
}
