package com.example.ejb3.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator

import groovy.transform.CompileStatic
import org.hibernate.envers.Audited

@Audited
@Entity
@CompileStatic
class AuditedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = 'AUDITED_ITEM_SEQ')
    @SequenceGenerator(name = 'AUDITED_ITEM_SEQ', sequenceName = 'AUDITED_ITEM_SEQ')
    long id
    @Column(unique = true)
    String name

}
