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
package beandsl.example.plugin

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * End-to-end proof that {@code field(...)} and {@code method(...)} work as real class members on
 * the generated sibling - not just structurally (as {@link org.grails.compiler.beans.GrailsBeansASTTransformationSpec}
 * already proves at the AST level) but with a genuinely booted Spring context, so the
 * {@code @Value} placeholder on the generated field is actually resolved from real application
 * properties, not merely present as an annotation.
 */
class GreetingGrailsPluginAutoDiscoverySpec extends Specification {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = [AopAutoConfiguration])
    static class TestApp {
    }

    @AutoCleanup
    ConfigurableApplicationContext context

    def "a field(...)'s @Value is resolved from real properties, and method(...) is callable from bean(...)"() {
        when:
        context = new SpringApplicationBuilder(TestApp)
                .web(WebApplicationType.NONE)
                .properties('beandsl.example.greeting-suffix=!!!')
                .run()

        then:
        context.getBean('greeting', Greeting).text == 'Hello, World!!!'
    }

    def "field(...)'s default value applies when the property is unset"() {
        when:
        context = new SpringApplicationBuilder(TestApp)
                .web(WebApplicationType.NONE)
                .run()

        then:
        context.getBean('greeting', Greeting).text == 'Hello, World!'
    }

    def "the generated sibling takes the plugin descriptor convention name"() {
        given: 'the GrailsPlugin suffix is replaced, not appended to'
        Class<?> sibling = Class.forName('beandsl.example.plugin.GreetingAutoConfiguration')

        expect:
        sibling.isAnnotationPresent(AutoConfiguration)

        and: 'the members the DSL declared landed on it, not on the plugin class'
        sibling.declaredFields*.name.contains('greetingSuffix')
        sibling.declaredMethods*.name.contains('buildGreeting')
        GreetingGrailsPlugin.declaredFields*.name.every { it != 'beans' }
    }

}
