package com.example.ejb3.auction;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class Persistent {

    @Id
    @GeneratedValue
    private Long id;

}
