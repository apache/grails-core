package com.example.ejb3.auction;

import javax.persistence.Entity;
import javax.persistence.Transient;

@Entity
public class BuyNow extends Bid {

    @Transient
    public boolean isBuyNow() {
        return true;
    }

    public String toString() {
        return super.toString() + " (buy now)";
    }
}
