package com.example.customconfig.auction

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class Item implements Serializable {

    private static final long serialVersionUID = 1L

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id

    String name

}
