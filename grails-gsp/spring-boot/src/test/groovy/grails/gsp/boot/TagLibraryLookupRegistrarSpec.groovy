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
package grails.gsp.boot

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition

import org.grails.web.pages.StandaloneTagLibraryLookup

import spock.lang.Specification

/**
 * How the tag libraries and the lookup that finds them are registered.
 *
 * <p>Every tag library takes the lookup - {@code TagLibraryInvoker} autowires it - so the lookup
 * must not in turn hold its tag libraries: that pair of dependencies is a cycle, and an application
 * had to allow circular references before it would start. They are beans of the context in their
 * own right, and the lookup finds them once they exist.
 */
class TagLibraryLookupRegistrarSpec extends Specification {

    private final GspAutoConfiguration.TagLibraryLookupRegistrar registrar =
            new GspAutoConfiguration.TagLibraryLookupRegistrar()

    private final BeanDefinitionRegistry registry = new DefaultListableBeanFactory()

    void 'every default tag library is registered as a bean of its own'() {
        when:
        registrar.registerBeanDefinitions(null, registry)

        then:
        registry.getBeanDefinition('renderTagLib').beanClassName ==
                GspAutoConfiguration.TagLibraryLookupRegistrar.DEFAULT_TAGLIB_CLASSES[0].name
        registry.getBeanDefinition('renderSitemeshTagLib').beanClassName ==
                GspAutoConfiguration.TagLibraryLookupRegistrar.DEFAULT_TAGLIB_CLASSES[1].name
        registry.getBeanDefinition('sitemesh3LayoutTagLib').beanClassName ==
                GspAutoConfiguration.TagLibraryLookupRegistrar.DEFAULT_TAGLIB_CLASSES[2].name
    }

    void 'the lookup is registered without holding the tag libraries'() {
        when:
        registrar.registerBeanDefinitions(null, registry)

        then: 'nothing the lookup depends on can depend back on it'
        registry.getBeanDefinition('gspTagLibraryLookup').beanClassName == StandaloneTagLibraryLookup.name
        !registry.getBeanDefinition('gspTagLibraryLookup').propertyValues.contains('tagLibInstances')

        and: 'it answers to the name a tag library is autowired by as well'
        ((DefaultListableBeanFactory) registry).getAliases('gspTagLibraryLookup') == ['tagLibraryLookup'] as String[]
    }

    void 'a tag library the application registers itself is left alone'() {
        given: 'an application that has replaced one of the default tag libraries'
        RootBeanDefinition ownTagLib = new RootBeanDefinition(StandaloneTagLibraryLookup)
        registry.registerBeanDefinition('renderTagLib', ownTagLib)

        when:
        registrar.registerBeanDefinitions(null, registry)

        then:
        registry.getBeanDefinition('renderTagLib').is(ownTagLib)
    }

}
