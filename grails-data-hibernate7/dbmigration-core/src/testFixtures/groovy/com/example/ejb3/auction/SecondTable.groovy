package com.example.ejb3.auction

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

import groovy.transform.CompileStatic

@Embeddable
@CompileStatic
class SecondTable {

    @Column(table = 'second_table')
    String secondName

}
