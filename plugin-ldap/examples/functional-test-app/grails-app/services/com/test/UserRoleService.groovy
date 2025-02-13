package com.test
import grails.gorm.services.Service

@Service(UserRole)
interface UserRoleService {
    UserRole save(User user, Role role)
}