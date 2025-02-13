package com.test

import grails.gorm.services.Service

@Service(User)
interface UserService {

    User save(String username, String password)
}