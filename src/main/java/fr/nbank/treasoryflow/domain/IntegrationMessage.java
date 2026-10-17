package fr.nbank.treasoryflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tf_integration_message")
public class IntegrationMessage {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String channelCode;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, length = 40)
    private String messageFormat;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String businessRef;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 64)
    private String createdBy;
}
