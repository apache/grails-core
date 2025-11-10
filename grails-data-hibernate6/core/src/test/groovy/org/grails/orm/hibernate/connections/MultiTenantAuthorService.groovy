package org.grails.orm.hibernate.connections

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant

@CurrentTenant
class MultiTenantAuthorService {
    int countAuthors() {
        MultiTenantAuthor.count()
    }

    @Tenant({ "moreBooks" })
    int countMoreAuthors() {
        MultiTenantAuthor.count()
    }
}
