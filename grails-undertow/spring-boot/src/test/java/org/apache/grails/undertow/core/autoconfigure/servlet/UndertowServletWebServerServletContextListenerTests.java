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

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.junit.jupiter.api.Test;

import org.apache.grails.undertow.core.servlet.UndertowServletWebServerFactory;

import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for Undertow driving {@link ServletContextListener}s correctly.
 */
class UndertowServletWebServerServletContextListenerTests {

    @Test
    void registeredServletContextListenerBeanIsCalled() {
        try (AnnotationConfigServletWebServerApplicationContext context =
                 new AnnotationConfigServletWebServerApplicationContext(
                     ServletListenerRegistrationBeanConfiguration.class, UndertowConfiguration.class)) {
            ServletContextListener servletContextListener = (ServletContextListener) context
                .getBean("registration", ServletListenerRegistrationBean.class)
                .getListener();
            then(servletContextListener).should().contextInitialized(any(ServletContextEvent.class));
        }
    }

    @Test
    void servletContextListenerBeanIsCalled() {
        try (AnnotationConfigServletWebServerApplicationContext context =
                 new AnnotationConfigServletWebServerApplicationContext(
                     ServletContextListenerBeanConfiguration.class, UndertowConfiguration.class)) {
            ServletContextListener servletContextListener = context.getBean("servletContextListener",
                ServletContextListener.class);
            then(servletContextListener).should().contextInitialized(any(ServletContextEvent.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UndertowConfiguration {

        @Bean
        UndertowServletWebServerFactory webServerFactory() {
            return new UndertowServletWebServerFactory(0);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class ServletContextListenerBeanConfiguration {

        @Bean
        ServletContextListener servletContextListener() {
            return mock(ServletContextListener.class);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class ServletListenerRegistrationBeanConfiguration {

        @Bean
        ServletListenerRegistrationBean<ServletContextListener> registration() {
            return new ServletListenerRegistrationBean<>(mock(ServletContextListener.class));
        }

    }

}
