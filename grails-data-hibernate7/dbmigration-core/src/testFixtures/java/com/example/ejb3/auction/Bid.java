package com.example.ejb3.auction;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorValue("Y")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Bid extends Persistent {
    @ManyToOne
    private AuctionItem item;
    private float amount;
    @Column(nullable = false, name = "datetime")
    private Date datetime;
    @ManyToOne(optional = false)
    private User bidder;

    public String toString() {
        return bidder.getUserName() + " $" + amount;
    }

    @Transient
    public boolean isBuyNow() {
        return false;
    }

}
