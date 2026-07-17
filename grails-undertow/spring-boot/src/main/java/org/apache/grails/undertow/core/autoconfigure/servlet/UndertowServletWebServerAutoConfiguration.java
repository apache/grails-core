/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.grails.undertow.core.autoconfigure.servlet;

import jakarta.servlet.ServletRequest;

import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.websockets.jsr.Bootstrap;
import org.xnio.SslClientAuthMode;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.thread.Threading;
import org.springframework.boot.web.server.autoconfigure.servlet.ServletWebServerConfiguration;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import org.apache.grails.undertow.core.UndertowBuilderCustomizer;
import org.apache.grails.undertow.core.autoconfigure.UndertowServerProperties;
import org.apache.grails.undertow.core.autoconfigure.UndertowWebServerConfiguration;
import org.apache.grails.undertow.core.servlet.UndertowDeploymentInfoCustomizer;
import org.apache.grails.undertow.core.servlet.UndertowServletWebServerFactory;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for an Undertow-based servlet web
 * server.
 *
 * @author Andy Wilkinson
 * @since 4.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ServletRequest.class, Undertow.class, SslClientAuthMode.class, DeploymentInfo.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(UndertowServerProperties.class)
@Import({UndertowWebServerConfiguration.class, ServletWebServerConfiguration.class})
public class UndertowServletWebServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
    UndertowServletWebServerFactory undertowServletWebServerFactory(
        ObjectProvider<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers,
        ObjectProvider<UndertowBuilderCustomizer> builderCustomizers) {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.getDeploymentInfoCustomizers().addAll(deploymentInfoCustomizers.orderedStream().toList());
        factory.getBuilderCustomizers().addAll(builderCustomizers.orderedStream().toList());
        return factory;
    }

    @Bean
    UndertowServletWebServerFactoryCustomizer undertowServletWebServerFactoryCustomizer(
        UndertowServerProperties undertowProperties) {
        return new UndertowServletWebServerFactoryCustomizer(undertowProperties);
    }

    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    UndertowDeploymentInfoCustomizer virtualThreadsUndertowDeploymentInfoCustomizer() {
        return (deploymentInfo) -> deploymentInfo.setExecutor(new VirtualThreadTaskExecutor("undertow-"));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Bootstrap.class)
    static class UndertowWebSocketConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "websocketServletWebServerCustomizer")
        WebSocketUndertowServletWebServerFactoryCustomizer websocketServletWebServerCustomizer() {
            return new WebSocketUndertowServletWebServerFactoryCustomizer();
        }

    }

}
