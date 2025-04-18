package org.grails.gorm.graphql

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@CompileStatic
abstract class HibernateSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared HibernateMappingContext mappingContext

    void setupSpec() {
        if (domainClasses) {
            hibernateDatastore = new HibernateDatastore(
                    DatastoreUtils.createPropertyResolver(configuration),
                    domainClasses as Class[])
        } else {
            hibernateDatastore = new HibernateDatastore(
                    DatastoreUtils.createPropertyResolver(configuration),
                    getPackage())
        }
        mappingContext = hibernateDatastore.mappingContext
    }

    /**
     * @return The configuration
     */
    Map<String, Object> getConfiguration() {
        [(Settings.SETTING_DB_CREATE): 'create-drop', (Settings.SETTING_DATASOURCE + '.logSql'):  true] as Map<String, Object>
    }

    /**
     * @return The domain classes
     */
    List<Class> getDomainClasses() { [] }

    @CompileDynamic
    Package getPackage() {
        getClass().getClassLoader().getPackage('org.grails.gorm.graphql.domain')
    }
}
