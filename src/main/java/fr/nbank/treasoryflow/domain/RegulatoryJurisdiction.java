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
@Table(name = "tf_jurisdiction")
public class RegulatoryJurisdiction {
    @Id
    @Column(length = 8)
    private String code;

    @Column(nullable = false)
    private String supervisor;

    @Column(nullable = false, length = 500)
    private String description;
}
