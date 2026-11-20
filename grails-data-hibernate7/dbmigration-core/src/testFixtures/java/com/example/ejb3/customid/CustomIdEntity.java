package com.example.ejb3.customid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity using a custom @IdGeneratorType-based ID generator.
 */
@Setter
@Getter
@Entity
@Table(name = "custom_id_entity")
public class CustomIdEntity {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
