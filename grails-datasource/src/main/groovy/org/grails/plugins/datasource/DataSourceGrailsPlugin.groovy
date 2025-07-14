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
package org.grails.plugins.datasource

import grails.core.GrailsApplication
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.GrailsUtil
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.transaction.ChainedTransactionManagerPostProcessor
import org.springframework.jmx.support.JmxUtils
import org.springframework.util.ClassUtils

import javax.sql.DataSource

/**
 * Handles the configuration of a DataSource within Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class DataSourceGrailsPlugin extends Plugin {

    private static final Log log = LogFactory.getLog(DataSourceGrailsPlugin)
    public static final String TRANSACTION_MANAGER_WHITE_LIST_PATTERN = 'grails.transaction.chainedTransactionManager.whitelistPattern'
    public static final String TRANSACTION_MANAGER_BLACK_LIST_PATTERN = 'grails.transaction.chainedTransactionManager.blacklistPattern'
    public static final String TRANSACTION_MANAGER_ENABLED = 'grails.transaction.chainedTransactionManager.enabled'
    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [core: version]

    @Override
    Closure doWithSpring() {
        { ->
            GrailsApplication application = grailsApplication

            if (pluginManager.hasGrailsPlugin('hibernate')) {

                if (!springConfig.unrefreshedApplicationContext?.containsBean('transactionManager')) {
                    Boolean enabled = config.getProperty(TRANSACTION_MANAGER_ENABLED, Boolean, false)
                    if (enabled) {
                        def whitelistPattern = config.getProperty(TRANSACTION_MANAGER_WHITE_LIST_PATTERN, '')
                        def blacklistPattern = config.getProperty(TRANSACTION_MANAGER_BLACK_LIST_PATTERN, '')
                        chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config, whitelistPattern ?: null, blacklistPattern ?: null)
                    }
                }
                if (ClassUtils.isPresent('org.h2.Driver', this.class.classLoader)) {
                    embeddedDatabaseShutdownHook(EmbeddedDatabaseShutdownHook)
                }

            } else {
                def dataSources = config.getProperty('dataSources', Map, [:])
                if (!dataSources) {
                    def defaultDataSource = config.getProperty('dataSource', Map)
                    if (defaultDataSource) {
                        dataSources['dataSource'] = defaultDataSource
                    }
                }
                if (dataSources) {
                    "dataSourceConnectionSources"(DataSourceConnectionSourcesFactoryBean, grailsApplication.config)
                    "dataSource"(InstanceFactoryBean, "#{dataSourceConnectionSources.defaultConnectionSource.source}", DataSource)
                }
            }

            if (config.getProperty('dataSource.jmxExport', Boolean, false) && ClassUtils.isPresent('org.apache.tomcat.jdbc.pool.DataSource', getClass().classLoader)) {
                try {
                    def jmxMBeanServer = JmxUtils.locateMBeanServer()
                    if (jmxMBeanServer) {
                        tomcatJDBCPoolMBeanExporter(TomcatJDBCPoolMBeanExporter) { bean ->
                            delegate.grailsApplication = application
                            server = jmxMBeanServer
                        }
                    }
                } catch (e) {
                    if (!Environment.isDevelopmentMode() && Environment.isWarDeployed()) {
                        log.warn("Cannot locate JMX MBeanServer. Disabling autoregistering dataSource pools to JMX.", e)
                    }
                }
            }
        }
    }

    @Override
    @CompileStatic
    void onShutdown(Map<String, Object> event) {
        if (!Environment.developmentEnvironmentAvailable || !Environment.isReloadingAgentEnabled()) {
            try {
                DataSourceUtils.clearJdbcDriverRegistrations()
            }
            catch (e) {
                log.debug "Error deregistering JDBC drivers: $e.message", e
            }
        }
    }

}
