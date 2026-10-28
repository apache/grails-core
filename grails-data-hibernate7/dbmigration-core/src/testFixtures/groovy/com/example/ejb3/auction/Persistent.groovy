package com.example.ejb3.auction

import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

import groovy.transform.CompileStatic

@MappedSuperclass
@CompileStatic
class Persistent {

    @Id
    @GeneratedValue
    Long id

}
