package org.grails.datastore.gorm.jdbc

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

import javax.sql.DataSource

/**
 * A data source that wraps a target datasource for multi-tenancy
 *
 * @since 6.1.1
 * @author Graeme Rocher
 */
@CompileStatic
@EqualsAndHashCode(includes = ['tenantId'])
class MultiTenantDataSource implements DataSource {

    final @Delegate DataSource target
    final String tenantId

    MultiTenantDataSource(DataSource target, String tenantId) {
        this.target = target
        this.tenantId = tenantId
    }
}
