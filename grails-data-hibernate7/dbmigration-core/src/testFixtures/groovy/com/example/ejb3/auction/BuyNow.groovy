package com.example.ejb3.auction

import jakarta.persistence.Entity
import jakarta.persistence.Transient

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class BuyNow extends Bid {

    @Transient
    boolean isBuyNow() {
        return true
    }

    String toString() {
        return super.toString() + ' (buy now)'
    }

}
