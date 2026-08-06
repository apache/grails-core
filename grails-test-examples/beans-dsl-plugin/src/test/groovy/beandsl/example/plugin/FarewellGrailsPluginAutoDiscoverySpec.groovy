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
 * End-to-end proof that a {@code beans} block written directly inside a {@code Plugin} subclass
 * is discovered purely via the generated {@code FarewellAutoConfiguration} sibling -
 * nothing here references that generated class by name, only {@code Farewell} itself.
 *
 * <p>{@code AopAutoConfiguration} is excluded because grails-core being on the classpath at all
 * activates Grails' own plugin-bootstrap {@code ApplicationContextInitializer} (registered via
 * {@code spring.factories}, unconditionally, regardless of this example's needs), which loads
 * {@code CoreGrailsPlugin} and registers its Groovy-aware AOP auto-proxy creator under Spring's
 * standard internal bean name - a class Boot's own {@code AopAutoConfiguration} does not
 * recognise when it tries to escalate that bean to class-proxying mode. Neither this test nor the
 * feature it demonstrates has anything to do with AOP proxying, so excluding it sidesteps an
 * unrelated clash rather than working around a bug in the DSL itself.
 */
class FarewellGrailsPluginAutoDiscoverySpec extends Specification {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = [AopAutoConfiguration])
    static class TestApp {
    }

    @AutoCleanup
    ConfigurableApplicationContext context

    def "the beans block inside a Plugin subclass is auto-discovered from its generated sibling"() {
        when:
        context = new SpringApplicationBuilder(TestApp)
                .web(WebApplicationType.NONE)
                .run()

        then:
        context.getBean('farewell', Farewell).say() == 'goodbye from a Plugin'
    }

    def "the plugin class itself carries neither the beans DSL nor @AutoConfiguration"() {
        expect:
        FarewellGrailsPlugin.declaredFields*.name.every { it != 'beans' }
        !FarewellGrailsPlugin.isAnnotationPresent(AutoConfiguration)

        and: "the generated sibling exists as a genuinely separate class"
        Class.forName('beandsl.example.plugin.FarewellAutoConfiguration').isAnnotationPresent(AutoConfiguration)
    }

}
