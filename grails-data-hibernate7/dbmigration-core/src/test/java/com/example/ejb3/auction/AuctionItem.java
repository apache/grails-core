package com.example.ejb3.auction;

import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class AuctionItem extends Persistent {
    @Column(length = 1000)
    private String description;
    @Column(length = 200)
    private String shortDescription;
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    private List<Bid> bids;
    @ManyToOne
    private Bid successfulBid;
    @ManyToOne
    private User seller;
    private Date ends;
    private int condition;

    public String toString() {
        return shortDescription + " (" + description + ": " + condition
                + "/10)";
    }

}
