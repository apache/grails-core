package com.example.ejb3.auction;

import javax.persistence.*;

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