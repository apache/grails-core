package com.example.community

import grails.gorm.annotation.AutoTimestamp
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

import java.time.LocalDateTime

@Entity(name = "CommunityUser")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id
    String firstName
    String lastName

    @AutoTimestamp(AutoTimestamp.EventType.CREATED) LocalDateTime created
    @AutoTimestamp LocalDateTime modified

    static constraints = {
        firstName blank: false
        lastName blank: false
        created nullable: true  // Grails 7 Bug
        modified nullable: true // Grails 7 Bug
    }

    static mapping = {
        table 'community_users'
    }
}
