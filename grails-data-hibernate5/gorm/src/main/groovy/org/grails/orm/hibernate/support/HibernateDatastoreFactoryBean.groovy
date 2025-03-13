package org.grails.orm.hibernate.support

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.AbstractHibernateDatastore
import org.hibernate.SessionFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.FactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.env.PropertyResolver
/**
 * Helper for constructing the datastore
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
public class HibernateDatastoreFactoryBean<T extends AbstractHibernateDatastore> implements FactoryBean<T>, ApplicationContextAware {

    private final Class<T> objectType;
    private final MappingContext mappingContext;
    private final SessionFactory sessionFactory;
    private final PropertyResolver configuration;
    private final String dataSourceName;
    ApplicationContext applicationContext;

    HibernateDatastoreFactoryBean(Class<T> objectType, MappingContext mappingContext, SessionFactory sessionFactory, PropertyResolver configuration, String dataSourceName) {
        this.objectType = objectType
        this.mappingContext = mappingContext
        this.sessionFactory = sessionFactory
        this.configuration = configuration
        this.dataSourceName = dataSourceName
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public T getObject() throws Exception {
        AbstractHibernateDatastore datastore = objectType.newInstance(mappingContext, sessionFactory, configuration, dataSourceName)


        if(applicationContext != null) {
            datastore.setApplicationContext(applicationContext)
        }

        return datastore;
    }

    @Override
    public Class<?> getObjectType() {
        return objectType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
