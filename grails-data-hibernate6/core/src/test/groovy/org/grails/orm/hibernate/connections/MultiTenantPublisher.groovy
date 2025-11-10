package org.grails.orm.hibernate.connections

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import org.grails.datastore.gorm.GormEntity

@Entity
class MultiTenantPublisher implements GormEntity<MultiTenantPublisher>, MultiTenant<MultiTenantPublisher> {
    Long id
    String tenantCode
    String name

    static mapping = MappingBuilder.orm {
        tenantId "tenantCode"
    }
}
