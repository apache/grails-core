package com.example.pojo.auction

import groovy.transform.CompileStatic

@CompileStatic
class User extends Persistent {

    String userName
    String password
    String email
    Name name
    List bids
    List auctions

    String toString() {
        return userName
    }

}
