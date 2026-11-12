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
package beandsl.example

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * End-to-end proof that {@link ExampleBeans} is discovered purely by Spring Boot's own
 * classpath-based auto-configuration import mechanism - nothing in this test references
 * {@code ExampleBeans} by name. The only reason Boot finds it is the
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * file the build generates by scanning compiled classes for {@code @AutoConfiguration}.
 *
 * <p>Deliberately {@code @EnableAutoConfiguration} rather than {@code @SpringBootApplication}:
 * the latter's implicit {@code @ComponentScan} would also pick up unrelated {@code @Configuration}
 * fixtures from sibling specs in this package, which is not what this test is proving.
 */
class ExampleBeansAutoDiscoverySpec extends Specification {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApp {
    }

    @AutoCleanup
    ConfigurableApplicationContext context

    def "the compiled DSL beans are auto-discovered on the classpath with no explicit reference"() {
        when:
        context = new SpringApplicationBuilder(TestApp)
                .web(WebApplicationType.NONE)
                .run()

        then:
        context.getBean('greeter', Greeter).greet() == 'hello from GrailsBeans'
        context.getBean('fancyGreeter', FancyGreeter).greet() == '*** hello ***'
        context.getBean('loudGreeter', LoudGreeter).greet() == 'HELLO FROM GRAILSBEANS'
    }

}
