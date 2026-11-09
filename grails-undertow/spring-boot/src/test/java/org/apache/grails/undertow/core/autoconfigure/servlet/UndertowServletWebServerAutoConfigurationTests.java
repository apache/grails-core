/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.grails.undertow.core.autoconfigure.servlet;

import org.junit.jupiter.api.Test;

import org.apache.grails.undertow.core.servlet.UndertowServletWebServerFactory;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link UndertowServletWebServerAutoConfiguration}.
 */
class UndertowServletWebServerAutoConfigurationTests {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(UndertowServletWebServerAutoConfiguration.class));

    @Test
    void providesUndertowServletWebServerFactory() {
        this.contextRunner.run((context) -> {
            assertThat(context).hasSingleBean(UndertowServletWebServerFactory.class);
            assertThat(context).hasSingleBean(UndertowServletWebServerFactoryCustomizer.class);
            assertThat(context).hasSingleBean(WebSocketUndertowServletWebServerFactoryCustomizer.class);
        });
    }

    @Test
    void backsOffWhenUserDefinedServletWebServerFactoryIsPresent() {
        this.contextRunner.withUserConfiguration(UserServletWebServerFactoryConfiguration.class)
            .run((context) -> assertThat(context).doesNotHaveBean(UndertowServletWebServerFactory.class));
    }

    @Test
    void backsOffOutsideServletWebApplication() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UndertowServletWebServerAutoConfiguration.class))
            .run((context) -> assertThat(context).doesNotHaveBean(UndertowServletWebServerFactory.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class UserServletWebServerFactoryConfiguration {

        @Bean
        ServletWebServerFactory servletWebServerFactory() {
            return mock(ServletWebServerFactory.class);
        }

    }

}
