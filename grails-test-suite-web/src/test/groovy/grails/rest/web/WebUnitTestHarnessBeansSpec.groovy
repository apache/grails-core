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
package grails.rest.web

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import spock.lang.Specification

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.mapping.LinkGenerator
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingsHolder

/**
 * The URL mappings holder, link generator and locale resolver a spec defines are kept, where the web test traits defined
 * their own in their place.
 */
class WebUnitTestHarnessBeansSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Closure doWithSpring() {
        { ->
            grailsUrlMappingsHolder(OwnUrlMappingsHolder)
            grailsLinkGenerator(DefaultLinkGenerator, 'https://own.example')
            localeResolver(FixedLocaleResolver, Locale.JAPAN)
        }
    }

    void 'the beans a spec defines are kept'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnUrlMappingsHolder
        applicationContext.getBean('grailsLinkGenerator').serverBaseURL == 'https://own.example'
        applicationContext.getBean('localeResolver').resolveLocale(request) == Locale.JAPAN
    }
}

/** The locale resolver the application configures, as the i18n plugin defines it, resolves the locale of a request. */
class WebUnitTestConfiguredLocaleResolverSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Closure doWithConfig() {
        { config -> config['grails.i18n.localeResolver'] = 'fixed' }
    }

    void 'a web unit test resolves the locale with the locale resolver the application configures'() {
        expect:
        applicationContext.getBean('localeResolver') instanceof FixedLocaleResolver
    }
}

class WebUnitTestDefaultLocaleResolverSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    void 'without a configured locale resolver, a web unit test resolves the locale of the session, as an application does'() {
        expect:
        applicationContext.getBean('localeResolver') instanceof SessionLocaleResolver
    }
}

/** The URL mappings plugin's beans, which a spec including the controllers plugin loads, are replaced, as in Grails 8. */
class WebUnitTestUrlMappingsPluginSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    void 'absolute links start with the server URL of the web test traits where the spec includes the URL mappings plugin'() {
        expect:
        controller.createLink(controller: 'book', action: 'show', id: 1, absolute: true) == 'http://localhost:8080/book/show/1'
    }
}

class WebUnitTestDeclaredLinkGeneratorSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    @Configuration
    static class LinkGeneratorConfiguration {

        @Bean
        LinkGenerator grailsLinkGenerator() {
            new DefaultLinkGenerator('https://own.example')
        }
    }

    void 'a link generator the spec declares is kept where it includes the URL mappings plugin'() {
        expect:
        controller.createLink(controller: 'book', action: 'show', id: 1, absolute: true) == 'https://own.example/book/show/1'
    }
}

class WebUnitTestInheritedLinkGeneratorSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    @Configuration
    static class LinkGeneratorConfiguration extends SharedLinkGeneratorBeans {
    }

    void 'a link generator the configuration class of the spec inherits is kept where it includes the URL mappings plugin'() {
        expect:
        controller.createLink(controller: 'book', action: 'show', id: 1, absolute: true) == 'https://shared.example/book/show/1'
    }
}

class WebUnitTestInheritedStaticLinkGeneratorSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    @Configuration
    static class LinkGeneratorConfiguration extends SharedStaticLinkGeneratorBeans {
    }

    void 'a link generator a static method the configuration class of the spec inherits declares is kept'() {
        expect:
        controller.createLink(controller: 'book', action: 'show', id: 1, absolute: true) == 'https://static.example/book/show/1'
    }
}

class WebUnitTestRegisteredUrlMappingsHolderSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('grailsUrlMappingsHolder', OwnUrlMappingsHolder)
        } as BeanRegistrar
    }

    void 'a URL mappings holder the spec registers is kept where it includes the URL mappings plugin'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnUrlMappingsHolder
    }
}

class WebUnitTestDefinedUrlMappingsHolderSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    Closure doWithSpring() {
        { ->
            grailsUrlMappingsHolder(OwnUrlMappingsHolder)
        }
    }

    void 'a URL mappings holder the spec defines is kept where it includes the URL mappings plugin'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnUrlMappingsHolder
    }
}

class WebUnitTestAliasedLinkGeneratorSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    Set<String> getIncludePlugins() {
        ['controllers'] as Set<String>
    }

    @Configuration
    static class LinkGeneratorConfiguration {

        @Bean(['ownLinkGenerator', 'grailsLinkGenerator'])
        LinkGenerator ownLinkGenerator() {
            new DefaultLinkGenerator('https://own.example')
        }
    }

    void 'a link generator the spec declares under another name, with an alias of the name of the link generator, is kept'() {
        expect:
        controller.createLink(controller: 'book', action: 'show', id: 1, absolute: true) == 'https://own.example/book/show/1'
    }
}

class WebUnitTestAliasedUrlMappingsHolderSpec extends Specification implements ControllerUnitTest<HarnessBeansController> {

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('ownUrlMappingsHolder', OwnUrlMappingsHolder)
            registry.registerAlias('ownUrlMappingsHolder', 'grailsUrlMappingsHolder')
        } as BeanRegistrar
    }

    void 'a URL mappings holder the spec registers under another name, with an alias of the name of the holder, is kept'() {
        expect:
        applicationContext.getBean('grailsUrlMappingsHolder') instanceof OwnUrlMappingsHolder
    }
}

@Artefact('Controller')
class HarnessBeansController { }

class OwnUrlMappingsHolder extends DefaultUrlMappingsHolder {

    OwnUrlMappingsHolder() {
        super([])
    }
}

abstract class SharedLinkGeneratorBeans {

    @Bean
    LinkGenerator grailsLinkGenerator() {
        new DefaultLinkGenerator('https://shared.example')
    }
}

abstract class SharedStaticLinkGeneratorBeans {

    @Bean
    static LinkGenerator grailsLinkGenerator() {
        new DefaultLinkGenerator('https://static.example')
    }
}
