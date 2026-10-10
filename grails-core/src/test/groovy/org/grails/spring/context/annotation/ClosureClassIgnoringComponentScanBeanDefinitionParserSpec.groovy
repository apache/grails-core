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

import java.lang.reflect.Field

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.beans.factory.xml.XmlReaderContext
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.type.filter.TypeFilter
import spock.lang.Specification

import grails.plugins.GrailsPluginManager

/**
 * {@link #configureScanner} and its resource-pattern $-exclusion logic are exercised
 * end-to-end, using real Spring collaborators, via {@link GrailsComponentScanPostProcessorSpec},
 * whose {@code resourcePatternResolver()} builds the identical anonymous {@code AntPathMatcher}.
 * Real {@code ParserContext}/{@code Element} XML-parsing collaborators are heavy to construct
 * here without also driving a full namespace-handler parse, so this spec focuses on
 * {@link #createScanner}, the plugin-manager type-filter wiring specific to this class.
 */
class ClosureClassIgnoringComponentScanBeanDefinitionParserSpec extends Specification {

    ClosureClassIgnoringComponentScanBeanDefinitionParser parser = new ClosureClassIgnoringComponentScanBeanDefinitionParser()

    private static XmlReaderContext readerContextFor(DefaultListableBeanFactory registry) {
        new XmlBeanDefinitionReader(registry).createReaderContext(new ByteArrayResource(new byte[0]))
    }

    private static List<TypeFilter> includeFiltersOf(ClassPathBeanDefinitionScanner scanner) {
        Field field = ClassPathScanningCandidateComponentProvider.getDeclaredField('includeFilters')
        field.accessible = true
        (List<TypeFilter>) field.get(scanner)
    }

    void 'createScanner adds no include filters when there is no plugin manager to source them from'() {
        given:
        XmlReaderContext readerContext = readerContextFor(new DefaultListableBeanFactory())
        int defaultFilterCount = includeFiltersOf(parser.createScanner(readerContext, false)).size()

        when:
        ClassPathBeanDefinitionScanner scanner = parser.createScanner(readerContext, false)

        then: 'no plugin-manager filters are contributed beyond whatever the default scanner already carries'
        includeFiltersOf(scanner).size() == defaultFilterCount
    }

    void 'createScanner adds every type filter the plugin manager contributes'() {
        given:
        TypeFilter first = Stub(TypeFilter)
        TypeFilter second = Stub(TypeFilter)
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager) {
            getTypeFilters() >> [first, second]
        }
        int defaultFilterCount = includeFiltersOf(parser.createScanner(readerContextFor(new DefaultListableBeanFactory()), false)).size()

        DefaultListableBeanFactory parent = new DefaultListableBeanFactory()
        parent.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory(parent)

        when:
        ClassPathBeanDefinitionScanner scanner = parser.createScanner(readerContextFor(registry), false)
        List<TypeFilter> includeFilters = includeFiltersOf(scanner)

        then:
        includeFilters.size() == defaultFilterCount + 2
        includeFilters.containsAll([first, second])
    }

    void 'createScanner ignores a bean factory hierarchy with no plugin manager registered'() {
        given:
        DefaultListableBeanFactory parent = new DefaultListableBeanFactory()
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory(parent)
        int defaultFilterCount = includeFiltersOf(parser.createScanner(readerContextFor(registry), false)).size()

        when:
        ClassPathBeanDefinitionScanner scanner = parser.createScanner(readerContextFor(registry), false)

        then:
        includeFiltersOf(scanner).size() == defaultFilterCount
    }

}
