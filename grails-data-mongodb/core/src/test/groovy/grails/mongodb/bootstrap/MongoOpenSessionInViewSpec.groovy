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
package grails.mongodb.bootstrap

import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.springframework.context.support.GenericApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext

/**
 * Verifies the MongoDB open-session-in-view interceptor registers whenever the target registry is a
 * {@code WebApplicationContext}, independent of whether a {@code dispatcherServlet} bean definition has
 * been registered yet. This is the registration-order-independent branch of
 * {@code AbstractDatastoreInitializer#isWebApplicationRegistry} that lets the interceptor keep registering
 * once plugin beans drain before Spring Boot auto-configuration (which is what registers
 * {@code dispatcherServlet}).
 */
class MongoOpenSessionInViewSpec extends AutoStartedMongoSpec {

    void "the mongo open session in view interceptor registers in a web application context"() {
        given: "the initializer targeting a WebApplicationContext with no dispatcherServlet bean definition"
        def initializer = makeInitializer()
        def applicationContext = new GenericWebApplicationContext()

        when: "the context is configured and refreshed"
        initializer.configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()

        then: "the OSIV interceptor is registered"
        applicationContext.containsBean('mongoOpenSessionInViewInterceptor')

        cleanup:
        applicationContext.getBean(MongoDatastore).destroy()
        applicationContext.close()
    }

    void "the mongo open session in view interceptor is not registered outside a web application context (control)"() {
        given: "the same initializer targeting a plain, non-web ApplicationContext"
        def initializer = makeInitializer()
        def applicationContext = new GenericApplicationContext()

        when: "the context is configured and refreshed"
        initializer.configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()

        then: "the OSIV interceptor is not registered"
        !applicationContext.containsBean('mongoOpenSessionInViewInterceptor')

        cleanup:
        applicationContext.getBean(MongoDatastore).destroy()
        applicationContext.close()
    }

    private MongoDbDataStoreSpringInitializer makeInitializer() {
        new MongoDbDataStoreSpringInitializer([
                (MongoSettings.SETTING_HOST): mongoHost,
                (MongoSettings.SETTING_PORT): mongoPort,
        ]) {
            @Override
            protected Map<String, Class<?>> loadDataServices(String secondaryDatastore = null) {
                [:]
            }
        }
    }
}
