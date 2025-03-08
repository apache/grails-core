package org.grails.plugins.databasemigration.liquibase

import org.springframework.beans.factory.config.AbstractFactoryBean
import org.springframework.context.ApplicationContext

class GrailsLiquibaseFactory extends AbstractFactoryBean<GrailsLiquibase> {

    private final ApplicationContext applicationContext

    GrailsLiquibaseFactory(ApplicationContext applicationContext) {
        setSingleton(false)
        this.applicationContext = applicationContext
    }

    @Override
    Class<?> getObjectType() {
        return GrailsLiquibase
    }

    @Override
    protected GrailsLiquibase createInstance() throws Exception {
        return new GrailsLiquibase(applicationContext)
    }
}
