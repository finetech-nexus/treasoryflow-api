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
@Table(name = "tf_platform_parameter")
public class PlatformParameter {
    @Id
    @Column(length = 64)
    private String code;

    @Column(name = "parameter_value", nullable = false, length = 80)
    private String value;

    @Column(nullable = false, length = 24)
    private String unit;

    @Column(nullable = false, length = 240)
    private String label;
}
