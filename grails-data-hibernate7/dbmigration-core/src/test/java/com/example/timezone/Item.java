package com.example.timezone;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    @Column
    private Instant timestamp1;

    @Column
    private LocalDateTime timestamp2;

    @Column(columnDefinition = "timestamp")
    private Instant timestamp3;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime timestamp4;

}
