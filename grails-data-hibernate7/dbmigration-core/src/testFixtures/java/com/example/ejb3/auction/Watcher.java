package com.example.ejb3.auction;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;

@Entity
public class Watcher {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "WATCHER_SEQ")
    @TableGenerator(name = "WATCHER_SEQ", table = "WatcherSeqTable")
    private Integer id;

    @SuppressWarnings("unused")
    private String name;

    @ManyToOne
    private AuctionItem auctionItem;
}