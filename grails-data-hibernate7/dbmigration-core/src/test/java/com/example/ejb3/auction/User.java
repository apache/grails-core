package com.example.ejb3.auction;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class User extends Persistent {
    private String userName;
    private String password;
    private String email;
    private Name name;
    @OneToMany(mappedBy = "bidder", cascade = CascadeType.ALL)
    private List<Bid> bids;
    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL)
    private List<AuctionItem> auctions;

    public String toString() {
        return userName;
    }

}
