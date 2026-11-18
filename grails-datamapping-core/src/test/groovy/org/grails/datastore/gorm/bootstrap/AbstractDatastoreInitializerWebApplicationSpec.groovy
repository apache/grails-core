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
package org.grails.datastore.gorm.bootstrap

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import spock.lang.Specification

/**
 * Unit coverage for the web-application detection that guards open-session-in-view registration in
 * the Hibernate and MongoDB datastore initializers. Because plugin beans now register before Spring
 * Boot auto-configuration, the check can no longer rely on the {@code dispatcherServlet} bean being
 * present, so it also treats the web application context itself as the signal.
 *
 * <p>The web-application-context branch depends on {@code spring-web}, which this module does not
 * pull in (that is exactly why the check resolves the type reflectively); that branch is covered
 * end-to-end by the Hibernate open-session-in-view integration tests in a real web application.
 */
class AbstractDatastoreInitializerWebApplicationSpec extends Specification {

    private static boolean isWeb(BeanDefinitionRegistry registry) {
        new TestDatastoreInitializer().isWebApplicationRegistry(registry)
    }

    void 'a null registry is not a web application'() {
        expect:
        !isWeb(null)
    }

    void 'a registry with a dispatcherServlet bean definition is a web application'() {
        given:
        def registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('dispatcherServlet', new RootBeanDefinition(Object))

        expect:
        isWeb(registry)
    }

    void 'a plain registry with neither a dispatcherServlet nor a web context type is not a web application'() {
        expect:
        !isWeb(new DefaultListableBeanFactory())
    }

    static class TestDatastoreInitializer extends AbstractDatastoreInitializer {
        Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) { { -> } }
        protected Class getPersistenceInterceptorClass() { null }
    }
}
