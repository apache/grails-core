package com.example.ejb3.auction;

import jakarta.persistence.*;
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
