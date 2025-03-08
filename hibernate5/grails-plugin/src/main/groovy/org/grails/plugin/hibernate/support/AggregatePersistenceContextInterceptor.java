package org.grails.plugin.hibernate.support;

import org.grails.orm.hibernate.AbstractHibernateDatastore;

/**
 * Concrete implementation of the {@link AbstractMultipleDataSourceAggregatePersistenceContextInterceptor} class for Hibernate 4
 *
 * @author Graeme Rocher
 * @author Burt Beckwith
 */
public class AggregatePersistenceContextInterceptor extends AbstractMultipleDataSourceAggregatePersistenceContextInterceptor {

    public AggregatePersistenceContextInterceptor(AbstractHibernateDatastore hibernateDatastore) {
        super(hibernateDatastore);
    }

    @Override
    protected SessionFactoryAwarePersistenceContextInterceptor createPersistenceContextInterceptor(String dataSourceName) {
        HibernatePersistenceContextInterceptor interceptor = new HibernatePersistenceContextInterceptor(dataSourceName);
        AbstractHibernateDatastore datastoreForConnection = hibernateDatastore.getDatastoreForConnection(dataSourceName);
        interceptor.setHibernateDatastore(datastoreForConnection);
        return interceptor;
    }

}
