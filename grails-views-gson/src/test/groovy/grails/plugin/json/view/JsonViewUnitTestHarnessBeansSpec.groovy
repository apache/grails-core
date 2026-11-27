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
package grails.plugin.json.view

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import org.springframework.web.servlet.i18n.SessionLocaleResolver

import grails.views.json.test.JsonViewUnitTest
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import spock.lang.Specification

/**
 * The URL mappings holder, link generator and locale resolver a JSON view spec defines are kept, as the web test traits
 * keep them, where the trait defined its own in their place.
 */
class JsonViewUnitTestHarnessBeansSpec extends Specification implements JsonViewUnitTest {

    Closure doWithSpring() {
        { ->
            grailsUrlMappingsHolder(OwnJsonViewUrlMappingsHolder)
            grailsLinkGenerator(DefaultLinkGenerator, 'https://own.example')
            localeResolver(FixedLocaleResolver, Locale.JAPAN)
        }
    }

    void 'the URL mappings holder, link generator and locale resolver a JSON view spec defines are kept'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnJsonViewUrlMappingsHolder
        applicationContext.getBean('grailsLinkGenerator').serverBaseURL == 'https://own.example'
        applicationContext.getBean('localeResolver') instanceof FixedLocaleResolver
    }
}

class JsonViewUnitTestRegisteredBeansSpec extends Specification implements JsonViewUnitTest {

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('grailsUrlMappingsHolder', OwnJsonViewUrlMappingsHolder)
            registry.registerBean('ownLinkGenerator', DefaultLinkGenerator) { spec ->
                spec.supplier { new DefaultLinkGenerator('https://registered.example') }
            }
            registry.registerAlias('ownLinkGenerator', 'grailsLinkGenerator')
        } as BeanRegistrar
    }

    void 'a URL mappings holder, and a link generator under an alias of its name, a JSON view spec registers are kept'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnJsonViewUrlMappingsHolder
        applicationContext.getBean('grailsLinkGenerator').serverBaseURL == 'https://registered.example'
    }
}

class JsonViewUnitTestConfiguredBeansSpec extends Specification implements JsonViewUnitTest {

    @Configuration
    static class OwnBeans {

        @Bean
        UrlMappingsHolder grailsUrlMappingsHolder() {
            new OwnJsonViewUrlMappingsHolder()
        }

        @Bean
        LinkGenerator grailsLinkGenerator() {
            new DefaultLinkGenerator('https://configured.example')
        }
    }

    void 'the URL mappings holder and link generator of a configuration class of a JSON view spec are kept'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnJsonViewUrlMappingsHolder
        applicationContext.getBean('grailsLinkGenerator').serverBaseURL == 'https://configured.example'
    }
}

class JsonViewUnitTestDeclaredLinkGeneratorSpec extends Specification implements JsonViewUnitTest {

    Closure doWithSpring() {
        { ->
            grailsUrlMappingsHolder(OwnJsonViewUrlMappingsHolder)
            grailsLinkGenerator(DefaultLinkGenerator, 'https://own.example')
        }
    }

    void 'a JSON view links with the link generator the spec defines'() {
        expect:
        render('json { href g.link(controller: "book", action: "show", id: 1, absolute: true) }').json.href ==
                'https://own.example/book/show/1'
    }
}

class JsonViewUnitTestConfiguredLocaleResolverSpec extends Specification implements JsonViewUnitTest {

    Closure doWithConfig() {
        { config -> config['grails.i18n.localeResolver'] = 'fixed' }
    }

    void 'a JSON view unit test resolves locales with the locale resolver the application configures'() {
        expect:
        applicationContext.getBean('localeResolver') instanceof FixedLocaleResolver
    }
}

class JsonViewUnitTestDefaultLocaleResolverSpec extends Specification implements JsonViewUnitTest {

    void 'without a configured locale resolver, a JSON view unit test resolves the locale of the session, as in Grails 8'() {
        expect:
        applicationContext.getBean('localeResolver') instanceof SessionLocaleResolver
    }
}

class OwnJsonViewUrlMappingsHolder extends DefaultUrlMappingsHolder {

    OwnJsonViewUrlMappingsHolder() {
        super([])
    }
}
