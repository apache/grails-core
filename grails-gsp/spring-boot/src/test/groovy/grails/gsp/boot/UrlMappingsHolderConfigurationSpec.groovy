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

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import grails.web.mapping.UrlMappingsHolder
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingsHolder

import spock.lang.Specification

/**
 * The URL mappings a link generator is given in an application that declares none of its own. A
 * generator requires them, and the asset pipeline contributes a generator to an application that
 * routes with Spring MVC and has no {@code UrlMappings} artefact for them to come from.
 *
 * <p>The configuration under test is a nested one of {@link GspAutoConfiguration} and is run here on
 * its own, as the conditions are what it contributes. No example application exercises it: one is
 * reached only by keeping grails-web-url-mappings on the class path, which the gsp-spring-boot
 * example excludes, so this specification is the whole of its coverage.
 */
class UrlMappingsHolderConfigurationSpec extends Specification {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GspAutoConfiguration.UrlMappingsHolderConfiguration))

    void 'a link generator starts in an application that maps no URLs'() {
        expect: 'the generator finds the mappings it requires, empty though they are'
        runner.withUserConfiguration(LinkGeneratorConfiguration).run { context ->
            assert !context.startupFailure
            assert context.getBean(DefaultLinkGenerator).urlMappingsHolder != null
        }
    }

    void 'an application that maps no URLs is given an empty holder'() {
        expect:
        runner.run { context ->
            assert context.getBean('grailsUrlMappingsHolder', UrlMappingsHolder).urlMappings.length == 0
        }
    }

    void 'an application that maps URLs keeps its own'() {
        expect: 'the holder of a Grails application, or of one declaring its own, is left alone'
        runner.withUserConfiguration(OwnHolderConfiguration).run { context ->
            assert context.getBean('grailsUrlMappingsHolder').is(OwnHolderConfiguration.HOLDER)
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LinkGeneratorConfiguration {

        @Bean
        DefaultLinkGenerator grailsLinkGenerator() {
            new DefaultLinkGenerator('http://localhost:8080')
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class OwnHolderConfiguration {

        static final UrlMappingsHolder HOLDER = new DefaultUrlMappingsHolder([])

        @Bean
        UrlMappingsHolder grailsUrlMappingsHolder() {
            HOLDER
        }

    }

}
