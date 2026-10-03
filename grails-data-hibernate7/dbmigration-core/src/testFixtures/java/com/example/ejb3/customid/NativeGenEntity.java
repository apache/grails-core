package com.example.ejb3.customid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NativeGenerator;

/**
 * Entity using @NativeGenerator, matching the user's UserB pattern.
 */
@Setter
@Getter
@Entity
@Table(name = "native_gen_entity")
public class NativeGenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @NativeGenerator(sequenceForm = @SequenceGenerator(
            name = "native_gen_seq",
            sequenceName = "native_gen_seq",
            allocationSize = 22,
            initialValue = 3
    ))
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "native_gen_seq")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
