package com.example

import grails.gorm.annotation.AutoTimestamp
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

import java.time.LocalDateTime

class User implements UserDetails {

    String firstName
    String lastName
    String email
    String password
    String roles = ''

    boolean accountNonExpired = true
    boolean accountNonLocked = true
    boolean credentialsNonExpired = true
    boolean enabled = true

    @AutoTimestamp(AutoTimestamp.EventType.CREATED) LocalDateTime created
    @AutoTimestamp LocalDateTime modified

    static constraints = {
        firstName blank: false
        lastName blank: false
        email email: true, blank: false
        password password:true
        created nullable: true  // Grails 7 Bug
        modified nullable: true // Grails 7 Bug
    }

    static mapping = {
        table 'users'
    }

    @Override
    Collection<? extends GrantedAuthority> getAuthorities() {
        roles.split('').collect { new SimpleGrantedAuthority(it) }
    }

    @Override
    String getUsername() { email }
}
