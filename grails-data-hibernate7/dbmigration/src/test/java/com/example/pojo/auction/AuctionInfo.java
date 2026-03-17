package com.example.pojo.auction;

import lombok.Getter;

import java.util.Date;

@Getter
public class AuctionInfo {
    private long id;
    private String description;
    private Date ends;
    private Float maxAmount;

    public AuctionInfo(long id, String description, Date ends, Float maxAmount) {
        this.id = id;
        this.description = description;
        this.ends = ends;
        this.maxAmount = maxAmount;
    }

}
