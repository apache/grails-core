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

import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverters

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * A controller unit test writes with the message converters Spring MVC would install, which Spring Boot's converter
 * customizers change.
 */
class ControllerUnitTestConverterCustomizerSpec extends Specification implements ControllerUnitTest<CustomizedConverterController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class CustomizerConfiguration {

        @Bean
        ServerHttpMessageConvertersCustomizer badgeConverterCustomizer() {
            new ServerHttpMessageConvertersCustomizer() {
                @Override
                void customize(HttpMessageConverters.ServerBuilder builder) {
                    builder.addCustomConverter(new BadgeConverter())
                }
            }
        }
    }

    void "respond writes with a converter a Spring Boot customizer bean adds, as Spring MVC does"() {
        given:
        response.format = 'json'

        when:
        controller.respond(new Badge(name: 'Grails'))

        then:
        response.contentAsString == 'badge:Grails'
    }
}

class ControllerUnitTestConverterBeanSpec extends Specification implements ControllerUnitTest<CustomizedConverterController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class ConverterConfiguration {

        @Bean
        BadgeConverter badgeConverter() {
            new BadgeConverter()
        }
    }

    void "respond writes with a converter bean of the application, which Spring Boot adds to Spring MVC's converters"() {
        given:
        response.format = 'json'

        when:
        controller.respond(new Badge(name: 'Grails'))

        then:
        response.contentAsString == 'badge:Grails'
    }
}

@Artefact('Controller')
class CustomizedConverterController { }

class Badge {
    String name
}

class BadgeConverter extends AbstractHttpMessageConverter<Badge> {

    BadgeConverter() {
        super(MediaType.APPLICATION_JSON)
    }

    @Override
    protected boolean supports(Class<?> type) {
        Badge.isAssignableFrom(type)
    }

    @Override
    protected Badge readInternal(Class<? extends Badge> type, HttpInputMessage message) {
        throw new UnsupportedOperationException()
    }

    @Override
    protected void writeInternal(Badge badge, HttpOutputMessage message) {
        message.body.write("badge:${badge.name}".getBytes('UTF-8'))
    }
}
