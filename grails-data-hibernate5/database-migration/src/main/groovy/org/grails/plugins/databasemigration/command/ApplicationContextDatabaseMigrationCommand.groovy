/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.command

import grails.config.ConfigMap
import grails.core.GrailsApplication
import grails.dev.commands.ExecutionContext
import grails.util.Environment
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import liquibase.database.Database
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import org.grails.config.PropertySourcesConfig
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.plugins.databasemigration.DatabaseMigrationTransactionManager
import org.grails.plugins.databasemigration.liquibase.GormDatabase
import org.grails.plugins.databasemigration.liquibase.GroovyChangeLogParser
import org.hibernate.dialect.Dialect
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.springframework.context.ConfigurableApplicationContext

import static org.grails.plugins.databasemigration.DatabaseMigrationGrailsPlugin.isDefaultDataSource
import static org.grails.plugins.databasemigration.PluginConstants.DEFAULT_DATASOURCE_NAME

@CompileStatic
trait ApplicationContextDatabaseMigrationCommand implements DatabaseMigrationCommand {

    ConfigurableApplicationContext applicationContext

    Boolean skipBootstrap = true

    boolean handle(ExecutionContext executionContext) {
        this.executionContext = executionContext
        handle()
        return true
    }

    void setExecutionContext(ExecutionContext executionContext) {
        this.commandLine = executionContext.commandLine
        this.contexts = optionValue('contexts')
        this.defaultSchema = optionValue('defaultSchema')
        this.dataSource = optionValue('dataSource') ?: DEFAULT_DATASOURCE_NAME
    }

    abstract void handle()

    @Override
    ConfigMap getConfig() {
        applicationContext.getBean(GrailsApplication).config
    }

    void withGormDatabase(ConfigurableApplicationContext applicationContext, String dataSource,
                          @ClosureParams(value = SimpleType, options = 'liquibase.database.Database') Closure closure) {
        def database = null
        try {
            database = createGormDatabase(applicationContext, dataSource)
            closure.call(database)
        } finally {
            database?.close()
        }
    }

    private Database createGormDatabase(ConfigurableApplicationContext applicationContext, String dataSource) {
        String sessionFactoryName = "sessionFactory"
        if (!isDefaultDataSource(dataSource)) {
            sessionFactoryName = sessionFactoryName + '_' + dataSource
        }

        def serviceRegistry = applicationContext.getBean(sessionFactoryName, SessionFactoryImplementor).serviceRegistry.parentServiceRegistry

        Dialect dialect = serviceRegistry.getService(JdbcServices.class).dialect

        HibernateDatastore hibernateDatastore = applicationContext.getBean("hibernateDatastore", HibernateDatastore)
        hibernateDatastore = hibernateDatastore.getDatastoreForConnection(dataSource)

        Database database = new GormDatabase(dialect, serviceRegistry, hibernateDatastore)
        configureDatabase(database)

        return database
    }

    ConfigMap getEnvironmentConfig(String environment) {
        return (ConfigMap) environmentWith(environment) {
            new PropertySourcesConfig(((PropertySourcesConfig) config).getPropertySources())
        }
    }

    private Object environmentWith(String environment, Closure closure) {
        def originalEnvironment = Environment.current
        System.setProperty(Environment.KEY, environment)
        try {
            return closure.call()
        } finally {
            System.setProperty(Environment.KEY, originalEnvironment.name)
        }
    }

    void withTransaction(Closure callable) {
        new DatabaseMigrationTransactionManager(this.applicationContext, this.dataSource).withTransaction(callable)
    }

    void configureLiquibase() {
        def groovyChangeLogParser = ChangeLogParserFactory.instance.parsers.find { ChangeLogParser changeLogParser -> changeLogParser instanceof GroovyChangeLogParser } as GroovyChangeLogParser
        groovyChangeLogParser.applicationContext = applicationContext
        groovyChangeLogParser.config = config
    }
}
