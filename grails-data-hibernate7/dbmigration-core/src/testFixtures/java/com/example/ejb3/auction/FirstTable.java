package com.example.ejb3.auction;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@SecondaryTable(name = "second_table", pkJoinColumns = @PrimaryKeyJoinColumn(name = "first_table_id"))
public class FirstTable {
    @Id
    private Long id;

    @Column(name = "name")
    private String name;

    @Embedded
    private SecondTable secondTable;

}
