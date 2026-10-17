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
@Table(name = "tf_counterparty")
public class Counterparty {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(length = 16)
    private String swiftBic;

    @Column(length = 160)
    private String website;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;
}
