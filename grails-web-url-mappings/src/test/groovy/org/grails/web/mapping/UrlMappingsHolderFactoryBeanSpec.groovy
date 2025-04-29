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
package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.web.mapping.UrlMappingsHolder
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Created by graemerocher on 17/10/16.
 */
class UrlMappingsHolderFactoryBeanSpec extends Specification {

    void "test url mappings holder factory bean excludes"() {
        given:
        UrlMappingsHolderFactoryBean factoryBean = new UrlMappingsHolderFactoryBean()
        def context = new GenericApplicationContext()
        context.refresh()
        def app = new DefaultGrailsApplication(ExcludeUrlMappings)
        def pm = new DefaultGrailsPluginManager(app)
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, app)
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, pm)
        app.initialise()
        factoryBean.setApplicationContext(context)

        when:"The URL mappings holder is created"
        factoryBean.afterPropertiesSet()
        UrlMappingsHolder holder = factoryBean.getObject()

        then:"The excludes are correct"
        holder.excludePatterns == ["/stomp/", "/stomp/*", "/topic/*"]
        holder.matchAll("/stomp/foo").size() == 0
    }
}
class ExcludeUrlMappings {

    static excludes = ["/stomp/", "/stomp/*", "/topic/*"]

    static mappings = {
        '/**'(controller:"index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
