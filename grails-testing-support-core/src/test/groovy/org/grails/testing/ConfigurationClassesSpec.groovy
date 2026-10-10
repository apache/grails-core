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
package org.grails.testing

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import grails.compiler.beans.GrailsBeans
import spock.lang.Specification

abstract class ConfigurationClassesBaseSpec extends Specification implements GrailsUnitTest {

    @Configuration
    static class InheritedConfiguration {

        @Bean
        StringBuilder inheritedBean() {
            new StringBuilder('inherited')
        }

        @Bean
        StringBuilder sharedBean() {
            new StringBuilder('base')
        }
    }
}

class ConfigurationClassesSpec extends ConfigurationClassesBaseSpec {

    @Configuration
    static class PlainConfiguration {

        @Bean
        StringBuilder plainBean() {
            new StringBuilder('plain')
        }

        @Bean
        StringBuilder sharedBean() {
            new StringBuilder('subclass')
        }
    }

    static class Helper {
    }

    static class Consumer {
        final Helper helper

        Consumer(Helper helper) {
            this.helper = helper
        }
    }

    @GrailsBeans
    @Configuration
    static class DslConfiguration {
        def beans = {
            bean('dslGreeting', StringBuilder) {
                new StringBuilder('hello from the beans DSL')
            }

            bean(Helper)

            bean('consumer', Consumer) { Helper helper ->
            }
        }
    }

    static class NotAConfiguration {

        @Bean
        StringBuilder ignoredBean() {
            new StringBuilder('ignored')
        }
    }

    void "a test's static nested @Configuration classes are registered by default"() {
        expect:
        configurationClasses == [InheritedConfiguration, PlainConfiguration, DslConfiguration] as Set
        applicationContext.getBean('plainBean').toString() == 'plain'

        and: 'a class without @Configuration is not'
        !applicationContext.containsBean('ignoredBean')
    }

    void "a nested @GrailsBeans class declares beans with the beans DSL"() {
        expect:
        applicationContext.getBean('dslGreeting').toString() == 'hello from the beans DSL'

        and: 'a bean with dependencies, constructed from its declared parameters'
        applicationContext.getBean('consumer', Consumer).helper.is(applicationContext.getBean('helper'))
    }

    void "a nested configuration class of a test it extends is registered too, and the subclass's wins a shared name"() {
        expect:
        applicationContext.getBean('inheritedBean').toString() == 'inherited'
        applicationContext.getBean('sharedBean').toString() == 'subclass'
    }
}
