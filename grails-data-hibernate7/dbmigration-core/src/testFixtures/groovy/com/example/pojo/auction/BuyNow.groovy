package com.example.pojo.auction;

public class BuyNow extends Bid {
    public boolean isBuyNow() {
        return true;
    }

    public String toString() {
        return super.toString() + " (buy now)";
    }
}
