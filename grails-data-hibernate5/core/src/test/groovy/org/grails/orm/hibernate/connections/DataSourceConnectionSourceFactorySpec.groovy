package org.grails.orm.hibernate.connections

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler
import org.hibernate.dialect.Oracle8iDialect
import spock.lang.Specification

/**
 * Created by graemerocher on 05/07/16.
 */
class DataSourceConnectionSourceFactorySpec extends Specification {

    void "test datasource connection source factory"() {
        when:
        DataSourceConnectionSourceFactory factory = new DataSourceConnectionSourceFactory()
        Map config = [
                'dataSource.url':"jdbc:h2:mem:dsConnDsFactorySpecDb;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': Oracle8iDialect.name,
                'dataSource.properties.dbProperties': [useSSL: false]
        ]
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config))

        then:"The connection source is correct"
        connectionSource.name == ConnectionSource.DEFAULT
        connectionSource.source

        when:"The schema names are resolved"
        def schemaNames = new DefaultSchemaHandler().resolveSchemaNames(connectionSource.source)

        then:"They are correct"
        schemaNames == ['INFORMATION_SCHEMA', 'PUBLIC']
    }
}
