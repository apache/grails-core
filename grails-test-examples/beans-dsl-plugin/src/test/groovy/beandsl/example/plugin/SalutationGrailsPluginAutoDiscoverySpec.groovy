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
 * End-to-end proof that a qualified {@code autoConfigurationName} survives the whole chain, not
 * just the AST: the sibling is generated into the package the attribute names, the build's
 * {@code AutoConfiguration.imports} scan records it under that qualified name, and Spring Boot
 * auto-discovers it from there. That chain is the entire point of accepting a qualified name -
 * what an {@code excludeName}, a {@code before=}/{@code after=} or a test import resolves is the
 * qualified name, so a sibling forced back into the descriptor's own package would not be the
 * class those references name.
 *
 * <p>{@code AopAutoConfiguration} is excluded for the same reason
 * {@link FarewellGrailsPluginAutoDiscoverySpec} excludes it - grails-core on the classpath
 * registers a Groovy-aware auto-proxy creator that Boot's own AOP autoconfiguration does not
 * recognise. It has nothing to do with the DSL.
 */
class SalutationGrailsPluginAutoDiscoverySpec extends Specification {

    private static final String SIBLING = 'beandsl.example.plugin.web.SalutationAutoConfiguration'

    private static final String IMPORTS_RESOURCE =
            '/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = [AopAutoConfiguration])
    static class TestApp {
    }

    @AutoCleanup
    ConfigurableApplicationContext context

    def "a sibling generated into another package is still auto-discovered by Spring Boot"() {
        when: "nothing here names the generated class - only Salutation itself"
        context = new SpringApplicationBuilder(TestApp)
                .web(WebApplicationType.NONE)
                .run()

        then:
        context.getBean('salutation', Salutation).say() == 'greetings from another package'
    }

    def "the generated sibling exists under its qualified name, and not in the plugin's own package"() {
        when:
        Class<?> sibling = Class.forName(SIBLING)

        then: "the qualified name the attribute gave is the class's real identity"
        sibling.isAnnotationPresent(AutoConfiguration)
        sibling.package.name == 'beandsl.example.plugin.web'

        and: "a bean method returning a type from the plugin's package resolved across the move"
        sibling.getDeclaredMethod('salutation').returnType == Salutation

        when: "the descriptor's own package is looked in instead"
        Class.forName('beandsl.example.plugin.SalutationAutoConfiguration')

        then:
        thrown(ClassNotFoundException)
    }

    def "the qualified name is what the generated AutoConfiguration.imports records"() {
        given:
        List<String> imports = getClass().getResource(IMPORTS_RESOURCE).text
                .readLines()*.trim()
                .findAll { it }

        expect: "an excludeName or AutoConfiguration.imports entry elsewhere resolves to this"
        SIBLING in imports

        and:
        !imports.contains('beandsl.example.plugin.SalutationAutoConfiguration')
    }

}
