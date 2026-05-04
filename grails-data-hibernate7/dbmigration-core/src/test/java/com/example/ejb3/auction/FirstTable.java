package com.example.ejb3.auction;

import javax.persistence.*;

@Entity
@SecondaryTable(name = "second_table", pkJoinColumns = @PrimaryKeyJoinColumn(name = "first_table_id"))
public class FirstTable {
    @Id
    private Long id;

    @Column(name = "name")
    private String name;

    @Embedded
    private SecondTable secondTable;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SecondTable getSecondTable() {
        return secondTable;
    }

    public void setSecondTable(SecondTable secondTable) {
        this.secondTable = secondTable;
    }
}