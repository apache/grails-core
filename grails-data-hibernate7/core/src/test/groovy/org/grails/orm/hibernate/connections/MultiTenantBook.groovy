package org.grails.orm.hibernate.connections

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import org.grails.datastore.gorm.GormEntity

@Entity
class MultiTenantBook implements GormEntity<MultiTenantBook>, MultiTenant<MultiTenantBook> {
    Long id
    Long version
    String tenantCode
    String title


    static belongsTo = [author: MultiTenantAuthor]
    static constraints = {
        title blank: false
    }

    static mapping = {
        tenantId name: "tenantCode"
    }
}
