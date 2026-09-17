package com.example.pojo.auction

import groovy.transform.CompileStatic

@CompileStatic
class Bid extends Persistent {

    AuctionItem item
    float amount
    Date datetime
    User bidder

    String toString() {
        return bidder.getUserName() + ' \$' + amount
    }

    boolean isBuyNow() {
        return false
    }

}
