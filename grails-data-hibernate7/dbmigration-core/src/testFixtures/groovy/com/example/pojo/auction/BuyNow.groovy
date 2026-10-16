package com.example.pojo.auction

import groovy.transform.CompileStatic

@CompileStatic
class BuyNow extends Bid {

    boolean isBuyNow() {
        return true
    }

    String toString() {
        return super.toString() + ' (buy now)'
    }

}
