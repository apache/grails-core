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

package grails.views.json.test.spock

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.servlet.i18n.SessionLocaleResolver

import grails.core.GrailsApplication
import grails.plugin.json.view.JsonViewGrailsPlugin
import grails.views.json.test.JsonViewUnitTest
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.testing.GrailsApplicationBuilder
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.UrlMappingsHolderFactoryBean

@CompileStatic
class JsonViewSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        JsonViewUnitTest test = (JsonViewUnitTest) invocation.instance
        setup(test)
        invocation.proceed()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void setup(JsonViewUnitTest test) {

        GrailsApplication grailsApp = test.grailsApplication
        def config = grailsApp.config
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) test.applicationContext).beanFactory
        boolean localeResolverPresent = test.applicationContext.containsBean('localeResolver')

        test.defineBeans {
            // a URL mappings holder and link generator the spec declares, and any locale resolver the context has, such
            // as the one grails.i18n.localeResolver configures, are kept, as the web test traits keep them
            if (!GrailsApplicationBuilder.isDeclaredByTest(beanFactory, 'grailsLinkGenerator')) {
                grailsLinkGenerator(DefaultLinkGenerator, config?.grails?.serverURL ?: 'http://localhost:8080')
            }
            if (!localeResolverPresent) {
                localeResolver(SessionLocaleResolver)
            }
            if (!GrailsApplicationBuilder.isDeclaredByTest(beanFactory, 'grailsUrlMappingsHolder')) {
                grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean) {
                    grailsApplication = grailsApp
                }
            }
            grailsDomainClassMappingContext(KeyValueMappingContext, 'test') {
                canInitializeEntities = true
            }
        }
        JsonViewGrailsPlugin plugin = new JsonViewGrailsPlugin()
        plugin.setApplicationContext(grailsApp.mainContext)
        test.defineBeans(plugin)
    }
}
