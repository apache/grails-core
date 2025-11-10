package grails.gorm.specs.multitenancy

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class User implements MultiTenant<User> {
    String username
    String tenantId

    static belongsTo = [Department]
    Department department


    static mapping = {
        table '`user`'
    }
}
