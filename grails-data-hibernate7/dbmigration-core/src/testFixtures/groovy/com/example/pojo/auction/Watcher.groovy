package com.example.pojo.auction

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne

import groovy.transform.CompileStatic

@Entity
@CompileStatic
class Watcher {

    @Id
    Integer id

    @SuppressWarnings('unused')
    String name

    @ManyToOne
    AuctionItem auctionItem

}
