package com.example.ejb3.auction;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class AuctionInfo {
    @Id
    private String id;
    @Column(length = 1000)
    private String description;
    private Date ends;
    private Float maxAmount;

    public AuctionInfo(String id, String description, Date ends, Float maxAmount) {
        this.id = id;
        this.description = description;
        this.ends = ends;
        this.maxAmount = maxAmount;
    }

}
