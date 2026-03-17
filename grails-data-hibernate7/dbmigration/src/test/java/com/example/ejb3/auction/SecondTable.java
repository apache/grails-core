package com.example.ejb3.auction;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
public class SecondTable {

    @Column(table = "second_table")
    @Getter
    @Setter
    private String secondName;

}
