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
package org.grails.spring.context.annotation

import java.nio.charset.StandardCharsets

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.type.filter.AssignableTypeFilter
import spock.lang.Specification

import grails.plugins.GrailsPluginManager
import org.grails.spring.context.annotation.fixture.ScanFixtureNestedHolder
import org.grails.spring.context.annotation.fixture.ScanFixtureUnannotated

/**
 * Exercises the parser the way an application does: through a {@code <grails:component-scan>}
 * element parsed by {@link XmlBeanDefinitionReader}, which resolves the Grails namespace via
 * {@code META-INF/spring.handlers} to {@link GrailsContextNamespaceHandler} and from there to this parser.
 */
class ClosureClassIgnoringComponentScanBeanDefinitionParserSpec extends Specification {

    static final String FIXTURE_PACKAGE = 'org.grails.spring.context.annotation.fixture'

    static DefaultListableBeanFactory scanWith(DefaultListableBeanFactory registry) {
        String xml = """\
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:grails="http://grails.org/schema/context">
    <grails:component-scan base-package="${FIXTURE_PACKAGE}"/>
</beans>
"""
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry)
        // the Grails schema is not on the classpath, so parse namespace-aware without XSD validation
        reader.validating = false
        reader.namespaceAware = true
        reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)))
        registry
    }

    static DefaultListableBeanFactory registryWithPluginManager(GrailsPluginManager pluginManager) {
        DefaultListableBeanFactory parent = new DefaultListableBeanFactory()
        parent.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
        new DefaultListableBeanFactory(parent)
    }

    static boolean registersClass(DefaultListableBeanFactory registry, Class<?> type) {
        registry.beanDefinitionNames.any { registry.getBeanDefinition(it).beanClassName == type.name }
    }

    void 'the grails component-scan element registers stereotyped components like Spring does'() {
        when:
        DefaultListableBeanFactory registry = scanWith(new DefaultListableBeanFactory())

        then:
        registry.containsBeanDefinition('scanFixtureComponent')
        !registersClass(registry, ScanFixtureUnannotated)
    }

    void 'class files whose name contains a dollar sign are skipped even when Spring would register them'() {
        given: 'a control: the stock Spring scanner does register the static nested component'
        DefaultListableBeanFactory plainSpring = new DefaultListableBeanFactory()
        new ClassPathBeanDefinitionScanner(plainSpring).scan(FIXTURE_PACKAGE)

        when:
        DefaultListableBeanFactory registry = scanWith(new DefaultListableBeanFactory())

        then:
        registersClass(plainSpring, ScanFixtureNestedHolder.Nested)
        !registersClass(registry, ScanFixtureNestedHolder.Nested)
        registry.containsBeanDefinition('scanFixtureComponent')
    }

    void 'type filters contributed by the plugin manager in the parent bean factory widen the scan'() {
        given:
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager) {
            getTypeFilters() >> [new AssignableTypeFilter(ScanFixtureUnannotated)]
        }

        when:
        DefaultListableBeanFactory registry = scanWith(registryWithPluginManager(pluginManager))

        then:
        registry.containsBeanDefinition('scanFixtureUnannotated')
        registry.containsBeanDefinition('scanFixtureComponent')
    }

    void 'a plugin manager without type filters leaves the scan unchanged'() {
        given:
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager) {
            getTypeFilters() >> []
        }

        when:
        DefaultListableBeanFactory registry = scanWith(registryWithPluginManager(pluginManager))

        then:
        registry.containsBeanDefinition('scanFixtureComponent')
        !registersClass(registry, ScanFixtureUnannotated)
    }

    void 'a bean factory hierarchy with no plugin manager registered is ignored'() {
        when:
        DefaultListableBeanFactory registry = scanWith(new DefaultListableBeanFactory(new DefaultListableBeanFactory()))

        then:
        registry.containsBeanDefinition('scanFixtureComponent')
        !registersClass(registry, ScanFixtureUnannotated)
    }

}
