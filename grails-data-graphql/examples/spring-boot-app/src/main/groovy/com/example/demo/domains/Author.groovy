package com.example.demo.domains

import grails.persistence.Entity
import org.grails.datastore.gorm.GormEntity

@Entity
class Author implements GormEntity<Author> {
    String name

    static graphql = true
}
