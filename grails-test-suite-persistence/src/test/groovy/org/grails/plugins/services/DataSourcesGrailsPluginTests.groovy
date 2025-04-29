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
package org.grails.plugins.services

import org.grails.commons.test.AbstractGrailsMockTests
import org.grails.plugins.DefaultGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.web.servlet.context.support.WebRuntimeSpringConfiguration
import org.springframework.context.ApplicationContext

class DataSourcesGrailsPluginTests  extends AbstractGrailsMockTests {

    void testSingleDataSource() {
        def appCtx = initializeContext([
                dataSource: [
                        pooled :true,
                        driverClassName:"org.h2.Driver",
                        username :"sa",
                        password :"",
                        dbCreate :"create-drop"
                ]
        ])
        assertTrue appCtx.containsBean("dataSource")
    }
    void testMultipleDataSources() {
        def appCtx = initializeContext([
                dataSource: [
                        pooled :true,
                        driverClassName:"org.h2.Driver",
                        username :"sa",
                        password :"",
                        dbCreate :"create-drop"
                ],
                dataSources: [
                        second:[
                                pooled :true,
                                driverClassName:"org.h2.Driver",
                                username :"sa",
                                password :"",
                                dbCreate :"create-drop"
                        ]
                ]
        ])
        assertTrue appCtx.containsBean("dataSource")
        assertTrue appCtx.containsBean("dataSource_second")
    }


    private ApplicationContext initializeContext(Map dataSources) {
        ga.getConfig().putAll(dataSources)
        def corePluginClass = gcl.loadClass("org.grails.plugins.CoreGrailsPlugin")
        def corePlugin = new DefaultGrailsPlugin(corePluginClass, ga)
        def dataSourcePluginClass = gcl.loadClass("org.grails.plugins.datasource.DataSourceGrailsPlugin")

        def dataSourcePlugin = new DefaultGrailsPlugin(dataSourcePluginClass, ga)
        def springConfig = new WebRuntimeSpringConfiguration(ctx)
        springConfig.servletContext = createMockServletContext()

        corePlugin.doWithRuntimeConfiguration(springConfig)
        dataSourcePlugin.manager = new MockGrailsPluginManager(ga)
        dataSourcePlugin.doWithRuntimeConfiguration(springConfig)

        springConfig.getApplicationContext()
    }
}
