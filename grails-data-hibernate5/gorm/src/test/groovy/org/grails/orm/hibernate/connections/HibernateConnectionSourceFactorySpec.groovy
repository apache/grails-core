package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.Oracle8iDialect
import spock.lang.Specification

/**
 * Created by graemerocher on 06/07/2016.
 */
class HibernateConnectionSourceFactorySpec extends Specification {

    void "Test hibernate connection factory"() {
        when:"A factory is used to create a session factory"

        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        Map config = [
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create'
        ]
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config))

        then:"The session factory is created"
        connectionSource.source instanceof SessionFactory
        connectionSource.source.getMetamodel().entity(Foo.name)
        connectionSource.source.openSession().createCriteria(Foo).list().size() == 0

        when:"The connection source is closed"
        connectionSource.close()

        then:"The session factory is closed"
        connectionSource.source.isClosed()
    }
}

@Entity
class Foo {
    String name
}
