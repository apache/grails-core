package com.example.pojo.auction;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class Watcher {

    @Id
    private Integer id;

    @SuppressWarnings("unused")
    private String name;

    @ManyToOne
    private AuctionItem auctionItem;
}
