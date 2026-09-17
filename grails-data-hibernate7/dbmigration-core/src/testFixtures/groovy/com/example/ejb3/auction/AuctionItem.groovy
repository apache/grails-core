package com.example.ejb3.auction

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class AuctionItem extends Persistent {

    @Column(length = 1000)
    String description
    @Column(length = 200)
    String shortDescription
    @OneToMany(mappedBy = 'item', cascade = CascadeType.ALL)
    List<Bid> bids
    @ManyToOne
    Bid successfulBid
    @ManyToOne
    User seller
    Date ends
    int condition

    String toString() {
        return shortDescription + ' (' + description + ': ' + condition +
                '/10)'
    }

}
