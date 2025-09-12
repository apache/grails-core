package com.example

import grails.gorm.transactions.Transactional
import grails.plugin.scaffolding.GormService
import grails.plugin.scaffolding.annotation.Scaffold
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

@Scaffold(GormService<User>)
@Transactional
class UserService implements UserDetailsService {
    @Override
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        def user = User.findByEmail(username)
        if (!user) {
            throw new UsernameNotFoundException("No user with email $username")
        }
        user
    }
}
