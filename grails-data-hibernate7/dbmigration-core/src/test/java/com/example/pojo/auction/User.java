package com.example.pojo.auction;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class User extends Persistent {
    private String userName;
    private String password;
    private String email;
    private Name name;
    private List bids;
    private List auctions;

    public String toString() {
        return userName;
    }

}
