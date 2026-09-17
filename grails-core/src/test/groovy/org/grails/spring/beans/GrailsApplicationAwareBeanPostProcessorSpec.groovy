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
package org.grails.spring.beans

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.core.support.GrailsConfigurationAware
import spock.lang.Specification

class GrailsApplicationAwareBeanPostProcessorSpec extends Specification {

    GrailsApplication grailsApplication = new DefaultGrailsApplication()
    GrailsApplicationAwareBeanPostProcessor processor = new GrailsApplicationAwareBeanPostProcessor(grailsApplication)

    void 'a GrailsApplicationAware bean is injected with the application'() {
        given:
        AwareBean bean = new AwareBean()

        when:
        Object result = processor.postProcessBeforeInitialization(bean, 'awareBean')

        then:
        result.is(bean)
        bean.grailsApplication.is(grailsApplication)
    }

    void 'a GrailsConfigurationAware bean is injected with the application config'() {
        given:
        ConfigAwareBean bean = new ConfigAwareBean()

        when:
        processor.postProcessBeforeInitialization(bean, 'configAwareBean')

        then:
        bean.configuration.is(grailsApplication.getConfig())
    }

    void 'a plain bean is returned unchanged'() {
        expect:
        processor.postProcessBeforeInitialization('plain', 'plainBean') == 'plain'
    }

    void 'processAwareInterfaces is usable directly as a static helper'() {
        given:
        AwareBean bean = new AwareBean()

        when:
        GrailsApplicationAwareBeanPostProcessor.processAwareInterfaces(grailsApplication, bean)

        then:
        bean.grailsApplication.is(grailsApplication)
    }

}

class AwareBean implements GrailsApplicationAware {

    GrailsApplication grailsApplication

    @Override
    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

}

class ConfigAwareBean implements GrailsConfigurationAware {

    Object configuration

    @Override
    void setConfiguration(grails.config.Config configuration) {
        this.configuration = configuration
    }

}
