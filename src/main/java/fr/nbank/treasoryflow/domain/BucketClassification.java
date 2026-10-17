package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_bucket_classification")
public class BucketClassification {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 32)
    private String formulaCode;

    @Column(nullable = false, length = 16)
    private String sourceType;

    @Column(nullable = false, length = 64)
    private String sourceKey;

    @Column(nullable = false, length = 64)
    private String bucket;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;
}
