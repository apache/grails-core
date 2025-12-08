package org.grails.orm.hibernate.connections

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

@Entity
class MultiTenantAuthor implements GormEntity<MultiTenantAuthor>, MultiTenant<MultiTenantAuthor> {
    Long id
    Long version
    String tenantId
    String name
    transient String tmp

    def beforeInsert() {
        tmp = "foo"
    }
    static hasMany = [books: MultiTenantBook]
    static constraints = {
        name blank: false
    }
}
