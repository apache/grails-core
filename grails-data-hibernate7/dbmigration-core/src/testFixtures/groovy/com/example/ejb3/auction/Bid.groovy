package com.example.ejb3.auction

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.ManyToOne
import jakarta.persistence.Transient

import groovy.transform.CompileStatic
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorValue('Y')
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@CompileStatic
class Bid extends Persistent {

    @ManyToOne
    AuctionItem item
    float amount
    @Column(nullable = false, name = 'datetime')
    Date datetime
    @ManyToOne(optional = false)
    User bidder

    String toString() {
        return bidder.getUserName() + ' \$' + amount
    }

    @Transient
    boolean isBuyNow() {
        return false
    }

}
