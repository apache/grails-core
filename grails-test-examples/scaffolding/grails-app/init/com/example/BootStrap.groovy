package com.example

class BootStrap {
    UserService userService

    def init = {
        if (!userService.count()) {
            User user = userService.save(new User(firstName: 'Test', lastName: 'User', email: 'test@grails.org', password: '{noop}letmein', roles: 'ROLE_USER'))
            println("User created with username: ${user.email} password: ${user.password.replaceAll(/\{.+\}/, '')}")
        }
    }

    def destroy = {
    }

}
