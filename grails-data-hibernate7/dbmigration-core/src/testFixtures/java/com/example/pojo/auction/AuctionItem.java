package com.example.pojo.auction;

import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionItem extends Persistent {
    private String description;
    private String shortDescription;
    private List bids;
    private Bid successfulBid;
    private User seller;
    private Date ends;
    private int condition;

    public String toString() {
        return shortDescription + " (" + description + ": " + condition + "/10)";
    }

}
