package com.example.pojo.auction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Watcher {

    @Id
    private Integer id;

    @SuppressWarnings("unused")
    private String name;

    @ManyToOne
    private AuctionItem auctionItem;
}
