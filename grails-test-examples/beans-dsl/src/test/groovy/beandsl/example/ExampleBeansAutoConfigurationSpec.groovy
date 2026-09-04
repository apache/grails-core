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

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

/**
 * Exercises the beans {@link ExampleBeans} contributes, including the
 * {@code .conditionalOnMissingBean(...)} back-off behaviour compiled from the DSL.
 */
class ExampleBeansAutoConfigurationSpec extends Specification {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ExampleBeans))

    def "registers greeter, fancyGreeter and loudGreeter with loudGreeter wired via constructor-style injection"() {
        expect:
        runner.run { AssertableApplicationContext context ->
            assertThat(context).hasSingleBean(Greeter)
            assertThat(context).hasSingleBean(FancyGreeter)
            assertThat(context).hasSingleBean(LoudGreeter)
            assertThat(context.getBean(Greeter).greet()).isEqualTo('hello from GrailsBeans')
            assertThat(context.getBean(FancyGreeter).greet()).isEqualTo('*** hello ***')
            assertThat(context.getBean(LoudGreeter).greet()).isEqualTo('HELLO FROM GRAILSBEANS')
        }
    }

    def "backs off fancyGreeter when the application already defines one"() {
        expect:
        runner.withUserConfiguration(CustomFancyGreeterConfig).run { AssertableApplicationContext context ->
            assertThat(context).hasSingleBean(FancyGreeter)
            assertThat(context.getBean(FancyGreeter).greet()).isEqualTo('custom')
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFancyGreeterConfig {

        @Bean
        FancyGreeter fancyGreeter() {
            new FancyGreeter() {
                @Override
                String greet() {
                    'custom'
                }
            }
        }

    }

}
