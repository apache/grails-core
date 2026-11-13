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

package org.grails.plugins.web.controllers

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.web.filter.FormContentFilter

import spock.lang.Specification

class GrailsFormContentFilterAutoConfigurationSpec extends Specification {

    void 'Boot WebMvcAutoConfiguration already contributes a FormContentFilter when active'() {
        expect: 'the contrast case proves the gap-filling assertions below are meaningful'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(FormContentFilter).length == 1
                }
    }

    void 'Grails fills the gap with a FormContentFilter when Boot WebMvcAutoConfiguration is absent'() {
        expect: 'the @EnableWebMvc case: Boot backs off, so Grails supplies the filter itself'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsFormContentFilterAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(FormContentFilter).length == 1
                }
    }

    void 'exactly one FormContentFilter is registered when both Boot and Grails auto-configs are present'() {
        expect: 'ordered after WebMvcAutoConfiguration, the Grails fallback backs off to Boot\'s filter'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration, GrailsFormContentFilterAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(FormContentFilter).length == 1
                }
    }

    void 'no FormContentFilter is registered when spring.mvc.formcontent.filter.enabled=false'() {
        expect: 'the explicit opt-out stays a true off-switch — Grails does not re-add the filter'
        new WebApplicationContextRunner()
                .withPropertyValues('spring.mvc.formcontent.filter.enabled=false')
                .withConfiguration(AutoConfigurations.of(GrailsFormContentFilterAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(FormContentFilter).length == 0
                }
    }

    void 'the Grails fallback backs off to an application-defined FormContentFilter'() {
        expect: 'a user bean wins and no second filter is added'
        new WebApplicationContextRunner()
                .withBean(FormContentFilter)
                .withConfiguration(AutoConfigurations.of(GrailsFormContentFilterAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(FormContentFilter).length == 1
                }
    }

    void 'a startup warning is registered when form-content parsing is disabled'() {
        expect: 'the opt-out is made visible when no filter ends up present'
        new WebApplicationContextRunner()
                .withPropertyValues('spring.mvc.formcontent.filter.enabled=false')
                .withConfiguration(AutoConfigurations.of(GrailsFormContentFilterAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(FormContentFilter).length == 0
                    assert context.getBeanNamesForType(GrailsFormContentFilterAutoConfiguration.FormContentParsingDisabledWarning).length == 1
                }
    }

    void 'no startup warning is registered when the filter is enabled'() {
        expect: 'no warning while form-content parsing is available'
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsFormContentFilterAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsFormContentFilterAutoConfiguration.FormContentParsingDisabledWarning).length == 0
                }
    }
}
