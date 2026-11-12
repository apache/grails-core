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

import javax.management.MBeanServer

import groovy.transform.CompileStatic

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment
import org.springframework.jmx.support.JmxUtils
import org.springframework.util.ClassUtils

import grails.plugins.Plugin
import grails.util.GrailsUtil
import org.grails.transaction.ChainedTransactionManagerPostProcessor

/**
 * Handles the configuration of a DataSource within Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
class DataSourceGrailsPlugin extends Plugin {

    private static final Log log = LogFactory.getLog(DataSourceGrailsPlugin)
    public static final String TRANSACTION_MANAGER_WHITE_LIST_PATTERN = 'grails.transaction.chainedTransactionManager.whitelistPattern'
    public static final String TRANSACTION_MANAGER_BLACK_LIST_PATTERN = 'grails.transaction.chainedTransactionManager.blacklistPattern'
    public static final String TRANSACTION_MANAGER_ENABLED = 'grails.transaction.chainedTransactionManager.enabled'
    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [core: version]

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            if (pluginManager.hasGrailsPlugin('hibernate')) {

                if (environment.getProperty(TRANSACTION_MANAGER_ENABLED, Boolean, false)) {
                    String whitelistPattern = environment.getProperty(TRANSACTION_MANAGER_WHITE_LIST_PATTERN, '')
                    String blacklistPattern = environment.getProperty(TRANSACTION_MANAGER_BLACK_LIST_PATTERN, '')
                    // The post-processor itself only rewires the context when a transactionManager
                    // definition and more than one chainable transaction manager exist, so the
                    // unrefreshed-context guard the bean DSL registration used is not needed here
                    registry.registerBean('chainedTransactionManagerPostProcessor', ChainedTransactionManagerPostProcessor) {
                        it.supplier {
                            new ChainedTransactionManagerPostProcessor(config, whitelistPattern ?: null, blacklistPattern ?: null)
                        }
                    }
                }
                if (ClassUtils.isPresent('org.h2.Driver', this.class.classLoader)) {
                    registry.registerBean('embeddedDatabaseShutdownHook', EmbeddedDatabaseShutdownHook)
                }

            } else {
                // Read through the Grails config, not the Environment: map-shaped subtrees are
                // not resolvable as Environment properties
                Map dataSources = config.getProperty('dataSources', Map, [:])
                if (!dataSources) {
                    Map defaultDataSource = config.getProperty('dataSource', Map)
                    if (defaultDataSource) {
                        dataSources['dataSource'] = defaultDataSource
                    }
                }
                if (dataSources) {
                    // The dataSource is an InstanceFactoryBean whose produced DataSource type must
                    // stay visible to Spring's factory-bean type prediction for by-type autowiring —
                    // which an instance supplier would hide — so the definitions are contributed by
                    // a dedicated post-processor instead.
                    registry.registerBean('dataSourceBeanDefinitionsPostProcessor', DataSourceBeanDefinitionsPostProcessor) {
                        it.infrastructure().supplier {
                            new DataSourceBeanDefinitionsPostProcessor(grailsApplication.config)
                        }
                    }
                }
            }

            if (environment.getProperty('dataSource.jmxExport', Boolean, false) &&
                    ClassUtils.isPresent('org.apache.tomcat.jdbc.pool.DataSource', getClass().classLoader)) {
                try {
                    MBeanServer jmxMBeanServer = JmxUtils.locateMBeanServer()
                    if (jmxMBeanServer) {
                        registry.registerBean('tomcatJDBCPoolMBeanExporter', TomcatJDBCPoolMBeanExporter) {
                            it.supplier {
                                TomcatJDBCPoolMBeanExporter exporter = new TomcatJDBCPoolMBeanExporter()
                                exporter.grailsApplication = grailsApplication
                                exporter.server = jmxMBeanServer
                                return exporter
                            }
                        }
                    }
                } catch (e) {
                    if (!grails.util.Environment.isDevelopmentMode() && grails.util.Environment.isWarDeployed()) {
                        log.warn('Cannot locate JMX MBeanServer. Disabling autoregistering dataSource pools to JMX.', e)
                    }
                }
            }
        }
    }

    @Override
    @CompileStatic
    void onShutdown(Map<String, Object> event) {
        if (!grails.util.Environment.developmentEnvironmentAvailable || !grails.util.Environment.isReloadingAgentEnabled()) {
            try {
                DataSourceUtils.clearJdbcDriverRegistrations()
            }
            catch (e) {
                log.debug("Error deregistering JDBC drivers: $e.message", e)
            }
        }
    }

}
