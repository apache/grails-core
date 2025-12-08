package grails.gorm.specs.multitenancy

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class Department implements MultiTenant<Department> {
    String name
    String tenantId

    static hasMany = [users: User]
}
