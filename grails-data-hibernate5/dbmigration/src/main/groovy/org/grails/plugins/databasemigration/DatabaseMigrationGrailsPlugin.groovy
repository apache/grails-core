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
package org.grails.plugins.databasemigration

import grails.plugins.Plugin
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import org.grails.plugins.databasemigration.liquibase.GrailsLiquibase
import org.grails.plugins.databasemigration.liquibase.GrailsLiquibaseFactory
import org.grails.plugins.databasemigration.liquibase.GroovyChangeLogParser
import org.springframework.context.ApplicationContext

import javax.sql.DataSource

class DatabaseMigrationGrailsPlugin extends Plugin {

    static final String CONFIG_MAIN_PREFIX = 'grails.plugin.databasemigration'

    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def pluginExcludes = [
            "**/testapp/**",
            "grails-app/views/error.gsp"
    ]

    def title = "Grails Database Migration Plugin" // Headline display name of the plugin
    def author = "Kazuki YAMAMOTO"
    def authorEmail = ""
    def description = 'Grails Database Migration Plugin'
    def documentation = "http://grails.org/plugin/database-migration"
    def license = "APACHE"
    def scm = [url: "https://github.com/grails-plugins/grails-database-migration"]

    @Override
    Closure doWithSpring() {
        configureLiquibase()
        return { ->
            grailsLiquibaseFactory(GrailsLiquibaseFactory, applicationContext)
        }
    }

    @Override
    void doWithApplicationContext() {
        def mainClassName = deduceApplicationMainClassName()

        def updateAllOnStart = config.getProperty("${CONFIG_MAIN_PREFIX}.updateAllOnStart", Boolean, false)

        dataSourceNames.each { String dataSourceName ->
            String configPrefix = isDefaultDataSource(dataSourceName) ? CONFIG_MAIN_PREFIX : "${CONFIG_MAIN_PREFIX}.${dataSourceName}"
            def skipMainClasses = config.getProperty("${configPrefix}.skipUpdateOnStartMainClasses", List, ['grails.ui.command.GrailsApplicationContextCommandRunner'])
            if (skipMainClasses.contains(mainClassName)) {
                return
            }

            if(!updateAllOnStart) {
                def updateOnStart = config.getProperty("${configPrefix}.updateOnStart", Boolean, false)
                if (!updateOnStart) {
                    return
                }
            } else {
                configPrefix = CONFIG_MAIN_PREFIX
            }

            new DatabaseMigrationTransactionManager(applicationContext, dataSourceName).withTransaction {
                GrailsLiquibase gl = applicationContext.getBean('grailsLiquibaseFactory', GrailsLiquibase)
                gl.dataSource = getDataSourceBean(applicationContext, dataSourceName)
                gl.dropFirst = config.getProperty("${configPrefix}.dropOnStart", Boolean, false)
                gl.changeLog = config.getProperty("${configPrefix}.updateOnStartFileName", String, isDefaultDataSource(dataSourceName) ? 'changelog.groovy' : "changelog-${dataSourceName}.groovy")
                gl.contexts = config.getProperty("${configPrefix}.updateOnStartContexts", List, []).join(',')
                gl.labels = config.getProperty("${configPrefix}.updateOnStartLabels", List, []).join(',')
                gl.defaultSchema = config.getProperty("${configPrefix}.updateOnStartDefaultSchema", String)
                gl.databaseChangeLogTableName = config.getProperty("${configPrefix}.databaseChangeLogTableName", String)
                gl.databaseChangeLogLockTableName = config.getProperty("${configPrefix}.databaseChangeLogLockTableName", String)
                gl.dataSourceName = getDataSourceName(dataSourceName)
                gl.afterPropertiesSet()
            }
        }
    }

    private def getDataSourceBean(ApplicationContext applicationContext, String dataSourceName) {
        applicationContext.getBean(getDataSourceName(dataSourceName), DataSource)
    }

    private void configureLiquibase() {
        def groovyChangeLogParser = ChangeLogParserFactory.instance.parsers.find { ChangeLogParser changeLogParser -> changeLogParser instanceof GroovyChangeLogParser } as GroovyChangeLogParser
        groovyChangeLogParser.applicationContext = applicationContext
        groovyChangeLogParser.config = config
    }

    private Set<String> getDataSourceNames() {
        def dataSources = config.getProperty('dataSources', Map, [:])
        if (!dataSources) {
            return ['dataSource']
        }
        Set<String> dataSourceNames = dataSources.keySet()
        if (!dataSourceNames.contains('dataSource')) {
            dataSourceNames = ['dataSource'] + dataSourceNames
        }
        dataSourceNames
    }

    private String deduceApplicationMainClassName() {
        new RuntimeException().stackTrace.find { StackTraceElement stackTraceElement -> 'main' == stackTraceElement.methodName }?.className
    }

    static String getDataSourceName(String dataSourceName) {
        isDefaultDataSource(dataSourceName) ? dataSourceName : "dataSource_$dataSourceName"
    }

    static Boolean isDefaultDataSource(String dataSourceName) {
        !dataSourceName || 'dataSource' == dataSourceName
    }
}
