package com.example.pojo.auction;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Bid extends Persistent {
    private AuctionItem item;
    private float amount;
    private Date datetime;
    private User bidder;

    public String toString() {
        return bidder.getUserName() + " $" + amount;
    }

    public boolean isBuyNow() {
        return false;
    }

}
