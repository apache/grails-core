package com.example.ejb3.auction

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class User extends Persistent {

    String userName
    String password
    String email
    Name name
    @OneToMany(mappedBy = 'bidder', cascade = CascadeType.ALL)
    List<Bid> bids
    @OneToMany(mappedBy = 'seller', cascade = CascadeType.ALL)
    List<AuctionItem> auctions

    String toString() {
        return userName
    }

}
