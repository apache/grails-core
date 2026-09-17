package com.example.pojo.auction

import groovy.transform.CompileStatic

@CompileStatic
class AuctionItem extends Persistent {

    String description
    String shortDescription
    List bids
    Bid successfulBid
    User seller
    Date ends
    int condition

    String toString() {
        return shortDescription + ' (' + description + ': ' + condition + '/10)'
    }

}
