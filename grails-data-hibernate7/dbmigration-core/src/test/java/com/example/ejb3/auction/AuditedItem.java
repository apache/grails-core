package com.example.ejb3.auction;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;

@Audited
@Entity
@Getter
@Setter
public class AuditedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "AUDITED_ITEM_SEQ")
    @SequenceGenerator(name = "AUDITED_ITEM_SEQ", sequenceName = "AUDITED_ITEM_SEQ")
    private long id;
    @Column(unique = true)
    private String name;

}
