/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.plugins.databasemigration.command

import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.persistence.Entity
import grails.util.GrailsNameUtils
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import org.grails.build.parsing.CommandLineParser
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.databasemigration.liquibase.GroovyChangeLogParser
import org.h2.Driver
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import spock.lang.AutoCleanup

abstract class ApplicationContextDatabaseMigrationCommandSpec extends DatabaseMigrationCommandSpec implements GrailsApplicationAware {

    GrailsApplication grailsApplication

    @AutoCleanup
    GenericApplicationContext applicationContext

    ApplicationCommand command

    Config config

    def setup() {
        applicationContext = new GenericApplicationContext()

        applicationContext.beanFactory.registerSingleton('dataSource', dataSource)
        applicationContext.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())

        def mutablePropertySources = new MutablePropertySources()
        mutablePropertySources.addFirst(new MapPropertySource('TestConfig', [
            'grails.plugin.databasemigration.changelogLocation': changeLogLocation.canonicalPath,
            'dataSource.dbCreate'                              : '',
            'environments.test.dataSource.url'                 : 'jdbc:h2:mem:testDb',
            'dataSource.username'                              : 'sa',
            'dataSource.password'                              : '',
            'dataSource.driverClassName'                       : Driver.name,
            'environments.other.dataSource.url'                : 'jdbc:h2:mem:otherDb',
        ]))
        config = new PropertySourcesConfig(mutablePropertySources)

        def datastoreInitializer = new HibernateDatastoreSpringInitializer(config, domainClasses)
        datastoreInitializer.configureForBeanDefinitionRegistry(applicationContext)

        applicationContext.refresh()

        def grailsApplication = applicationContext.getBean(GrailsApplication)
        grailsApplication.config = config

        def groovyChangeLogParser = ChangeLogParserFactory.instance.parsers.find { ChangeLogParser changeLogParser -> changeLogParser instanceof GroovyChangeLogParser } as GroovyChangeLogParser
        groovyChangeLogParser.applicationContext = applicationContext
        groovyChangeLogParser.config = config

        if (commandClass != null) {
            command = createCommand(commandClass)
        }
    }

    protected ApplicationCommand createCommand(Class<ApplicationCommand> applicationCommand) {
        def command = applicationCommand.getDeclaredConstructor().newInstance()
        command.applicationContext = applicationContext
        command.changeLogFile.parentFile.mkdirs()
        return command
    }

    protected Class[] getDomainClasses() {
        [] as Class[]
    }

    protected Class<ApplicationCommand> getCommandClass() {
        null
    }

    protected ExecutionContext getExecutionContext(Class<ApplicationCommand> clazz = commandClass, String... args) {
        def commandClassName = GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(clazz.name, 'Command'))
        new ExecutionContext(
            new CommandLineParser().parse(([commandClassName] + args.toList()) as String[])
        )
    }

    void cleanup() {

    }


}

@Entity
class Book {
    String title
    Author author
}

@Entity
class Author {
    String name
    static hasMany = [books: Book]
}
