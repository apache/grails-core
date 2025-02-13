package com.mycompany.myapp

import groovy.transform.CompileStatic

import org.springframework.ldap.core.DirContextAdapter
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper

@CompileStatic
class MyUserDetailsContextMapper implements UserDetailsContextMapper {

    @Override
    UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
        String email = ctx.attributes.get('mail')?.get() as String
        String phone = ctx.attributes.get('telephoneNumber')?.get() as String

        new MyUserDetails(username,
                '',
                true,
                true,
                true,
                true,
                authorities,
                email,
                phone)
    }

    @Override
    void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        throw new IllegalStateException("Only retrieving data from AD is currently supported")
    }
}
