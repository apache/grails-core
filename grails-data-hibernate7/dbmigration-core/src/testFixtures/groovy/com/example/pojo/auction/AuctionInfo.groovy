package com.example.pojo.auction

import groovy.transform.CompileStatic

@CompileStatic
class AuctionInfo {

    long id
    String description
    Date ends
    Float maxAmount

    AuctionInfo(long id, String description, Date ends, Float maxAmount) {
        this.id = id
        this.description = description
        this.ends = ends
        this.maxAmount = maxAmount
    }

}
