package com.example.ejb3.auction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class AuctionInfo {

    @Id
    String id
    @Column(length = 1000)
    String description
    Date ends
    Float maxAmount

    AuctionInfo(String id, String description, Date ends, Float maxAmount) {
        this.id = id
        this.description = description
        this.ends = ends
        this.maxAmount = maxAmount
    }

}
