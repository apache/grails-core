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
package org.grails.support

import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.ResolvableType
import spock.lang.Specification

class MockApplicationContextSpec extends Specification {

    MockApplicationContext ctx = new MockApplicationContext()

    void 'beans register, look up by name and type, and report absence'() {
        given:
        ctx.registerMockBean('a', 'stringBean')
        ctx.registerMockBean('b', 42)

        expect:
        ctx.containsBean('a')
        !ctx.containsBean('missing')
        ctx.getBean('a') == 'stringBean'
        ctx.getBean('a', String) == 'stringBean'
        ctx.getBean(String) == 'stringBean'
        ctx.getBeanNamesForType(String) == ['a'] as String[]
        ctx.getBeansOfType(String) == [a: 'stringBean']
        ctx.beanDefinitionCount == 2
        ctx.beanDefinitionNames.toList().sort() == ['a', 'b']
        ctx.containsBeanDefinition('a')
        ctx.getType('a') == String
        ctx.isTypeMatch('a', String)
        !ctx.isTypeMatch('a', Integer)
        ctx.getProperty('a') == 'stringBean'

        when:
        ctx.getBean('missing')

        then:
        thrown(NoSuchBeanDefinitionException)

        when:
        ctx.getBean('a', Integer)

        then:
        thrown(NoSuchBeanDefinitionException)
    }

    void 'the message source delegates through a registered messageSource bean'() {
        given:
        StaticMessageSource messageSource = new StaticMessageSource()
        messageSource.addMessage('greeting', Locale.ENGLISH, 'hello')
        ctx.registerMockBean('messageSource', messageSource)

        expect:
        ctx.getMessage('greeting', null, Locale.ENGLISH) == 'hello'
        ctx.getMessage('greeting', null, 'fallback', Locale.ENGLISH) == 'hello'

        when:
        MockApplicationContext withoutSource = new MockApplicationContext()
        withoutSource.getMessage('code', null, Locale.ENGLISH)

        then:
        // getBean() always throws NoSuchBeanDefinitionException rather than returning null, so the
        // null-check-then-BeanCreationException branch below it is unreachable in the real implementation.
        thrown(NoSuchBeanDefinitionException)
    }

    void 'mock resources are matched and can be unregistered'() {
        given:
        ctx.registerMockResource('/WEB-INF/grails-app/i18n/messages.properties', 'hello=world')

        expect:
        ctx.getResource('WEB-INF/grails-app/i18n/messages.properties').exists()
        ctx.getResource('WEB-INF/grails-app/i18n/messages.properties').inputStream.text == 'hello=world'
        ctx.getResources('WEB-INF/grails-app/i18n/*.properties').length == 1

        when:
        ctx.unregisterMockResource('WEB-INF/grails-app/i18n/messages.properties')

        then:
        !(ctx.getResource('WEB-INF/grails-app/i18n/messages.properties') instanceof MockApplicationContext.MockResource)
    }

    void 'ignored classpath locations resolve to null instead of a real classpath resource'() {
        given:
        ctx.registerIgnoredClassPathLocation('some/missing.properties')

        expect:
        ctx.getResource('some/missing.properties') == null

        when:
        ctx.unregisterIgnoredClassPathLocation('some/missing.properties')

        then:
        ctx.getResource('some/missing.properties') != null
    }

    void 'bean providers resolve through the registered bean and unsupported operations are reported'() {
        given:
        ctx.registerMockBean('x', 'value')

        expect:
        ctx.getBeanProvider(String).object == 'value'
        ctx.getBeanProvider(String).ifAvailable == 'value'
        ctx.getBeanProvider(String).ifUnique == 'value'
        ((ObjectProvider) ctx.getBeanProvider(ResolvableType.forClass(String))).object == 'value'

        when:
        ctx.getParent()

        then:
        thrown(UnsupportedOperationException)

        when:
        ctx.isSingleton('x')

        then:
        thrown(UnsupportedOperationException)

        when:
        ctx.containsLocalBean('x')

        then:
        thrown(UnsupportedOperationException)
    }

    void 'identity and environment accessors report the mock defaults'() {
        expect:
        ctx.id == 'MockApplicationContext'
        ctx.applicationName == ctx.id
        ctx.displayName == ctx.id
        ctx.startupDate > 0
        ctx.environment != null
        ctx.autowireCapableBeanFactory != null
        ctx.classLoader != null
        ctx.getAliases('x') == [] as String[]
        ctx.parentBeanFactory == null

        when:
        ctx.publishEvent(new Object())

        then:
        noExceptionThrown()
    }

}
