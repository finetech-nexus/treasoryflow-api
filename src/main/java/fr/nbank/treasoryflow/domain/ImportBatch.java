package fr.nbank.treasoryflow.domain;

import fr.nbank.treasoryflow.domain.Codes.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_import_batch", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tf_import_batch", columnNames = {"source_system", "external_batch_id"})
})
public class ImportBatch {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "external_batch_id", nullable = false, length = 128)
    private String externalBatchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportStatus status;

    @Column(nullable = false)
    private int accepted;

    @Column(nullable = false)
    private int rejected;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, length = 64)
    private String receivedBy;
}
