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

package org.grails.plugins

import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.web.servlet.plugins.GrailsWebPluginManager
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.DefaultGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.grails.spring.aop.autoproxy.GroovyAwareInfrastructureAdvisorAutoProxyCreator
import org.grails.web.servlet.context.support.WebRuntimeSpringConfiguration
import org.grails.commons.test.AbstractGrailsMockTests
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.beans.factory.config.RuntimeBeanReference

class CoreGrailsPluginTests extends AbstractGrailsMockTests {

    void testComponentScan() {
        def pluginClass = gcl.loadClass("org.grails.plugins.CoreGrailsPlugin")

        def plugin = new DefaultGrailsPlugin(pluginClass, ga)
        def pluginManager = new MockGrailsPluginManager()
        ctx.registerMockBean(GrailsPluginManager.BEAN_NAME, pluginManager)
        ga.config.grails.spring.bean.packages = ['org.grails.plugins.test']

        def springConfig = new WebRuntimeSpringConfiguration(ctx)
        springConfig.servletContext = createMockServletContext()

        plugin.doWithRuntimeConfiguration(springConfig)

        def appCtx = springConfig.getApplicationContext()

    }
    void testCorePlugin() {
        def pluginClass = gcl.loadClass("org.grails.plugins.CoreGrailsPlugin")

        def plugin = new DefaultGrailsPlugin(pluginClass, ga)

        def springConfig = new WebRuntimeSpringConfiguration(ctx)
        springConfig.servletContext = createMockServletContext()

        plugin.doWithRuntimeConfiguration(springConfig)

        def appCtx = springConfig.getApplicationContext()

        assert appCtx.containsBean("classLoader")
        assert appCtx.containsBean("customEditors")
        assert appCtx.getBean("org.springframework.aop.config.internalAutoProxyCreator") instanceof GroovyAwareAspectJAwareAdvisorAutoProxyCreator
    }

    void testDisableAspectj() {
        def pluginClass = gcl.loadClass("org.grails.plugins.CoreGrailsPlugin")

        def plugin = new DefaultGrailsPlugin(pluginClass, ga)

        def springConfig = new WebRuntimeSpringConfiguration(ctx)
        springConfig.servletContext = createMockServletContext()
        ga.config.grails.spring.disable.aspectj.autoweaving=true
        ga.configChanged()
        plugin.doWithRuntimeConfiguration(springConfig)

        def appCtx = springConfig.getApplicationContext()

        assert appCtx.containsBean("classLoader")
        assert appCtx.containsBean("customEditors")
        assert appCtx.getBean("org.springframework.aop.config.internalAutoProxyCreator") instanceof GroovyAwareInfrastructureAdvisorAutoProxyCreator

    }

    protected void onSetUp() {
        // needed for testBeanPropertyOverride
        gcl.parseClass("""
            class SomeTransactionalService {
                boolean transactional = true
                Integer i
            }
            class NonTransactionalService {
                boolean transactional = false
                Integer i
            }
        """)
    }

    /**
     * Tests the ability to set bean properties via the application config.
     *
     * @author Luke Daley
     */
    void testBeanPropertyOverride() {
        def co = new ConfigSlurper().parse('''
            dataSource {
                pooled = false
                driverClassName = "org.h2.Driver"
                username = "sa"
                password = ""
                dbCreate = "create-drop"
            }
            beans {
                someTransactionalService {
                    i = 1
                }
                nonTransactionalService {
                    i = 2
                }
            }
        ''')
        ga.config = new PropertySourcesConfig().merge(co)

        def corePluginClass = gcl.loadClass("org.grails.plugins.CoreGrailsPlugin")
        def corePlugin = new DefaultGrailsPlugin(corePluginClass,ga)
        def dataSourcePluginClass = gcl.loadClass("org.grails.plugins.datasource.DataSourceGrailsPlugin")
        def dataSourcePlugin = new DefaultGrailsPlugin(dataSourcePluginClass, ga)

        def springConfig = new WebRuntimeSpringConfiguration(ctx)

        def txMgr = springConfig.addSingletonBean("transactionManager", DataSourceTransactionManager)
        txMgr.addProperty("dataSource", new RuntimeBeanReference("dataSource"))
        springConfig.servletContext = createMockServletContext()

        corePlugin.doWithRuntimeConfiguration(springConfig)
        dataSourcePlugin.manager = new GrailsWebPluginManager([corePluginClass] as Class[], ga)
        dataSourcePlugin.doWithRuntimeConfiguration(springConfig)

        def pluginClass = gcl.loadClass("org.grails.plugins.services.ServicesGrailsPlugin")
        def plugin = new DefaultGrailsPlugin(pluginClass, ga)
        plugin.doWithRuntimeConfiguration(springConfig)

        def appCtx = springConfig.getApplicationContext()

        assertEquals(1, appCtx.getBean('someTransactionalService').i)
        assertEquals(2, appCtx.getBean('nonTransactionalService').i)

        // test that the overrides are applied on a reload - GRAILS-5763
        plugin.manager = [informObservers: { String pluginName, Map event -> }] as GrailsPluginManager
        plugin.applicationContext = appCtx

        ["SomeTransactionalService", "NonTransactionalService"].each {
            plugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, gcl.loadClass(it))
        }

    }
}
