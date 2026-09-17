package com.example.ejb3.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = 'ITEM_SEQ')
    @SequenceGenerator(name = 'ITEM_SEQ', sequenceName = 'ITEM_SEQ', initialValue = 1000, allocationSize = 100)
    long id
    @Column(unique = true)
    String name

}
