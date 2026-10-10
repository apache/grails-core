package com.example.ejb3.auction

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.SecondaryTable

import groovy.transform.CompileStatic

@Entity
@SecondaryTable(name = 'second_table', pkJoinColumns = @PrimaryKeyJoinColumn(name = 'first_table_id'))
@CompileStatic
class FirstTable {

    @Id
    Long id

    @Column(name = 'name')
    String name

    @Embedded
    SecondTable secondTable

}
