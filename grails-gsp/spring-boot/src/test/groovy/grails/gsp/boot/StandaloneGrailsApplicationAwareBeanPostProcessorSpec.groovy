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
package grails.gsp.boot

import org.springframework.beans.factory.ObjectProvider

import grails.config.Config
import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.core.support.GrailsConfigurationAware

import spock.lang.Specification

/**
 * The GSP beans that read what they need from the GrailsApplication - the page locator, the tag
 * library lookup, the JSP tag library resolver - get it here when no Grails plugin lifecycle has
 * run to give it to them.
 */
class StandaloneGrailsApplicationAwareBeanPostProcessorSpec extends Specification {

    private final Config config = Stub(Config)

    private final GrailsApplication grailsApplication = Stub(GrailsApplication) {
        getConfig() >> config
    }

    void 'a bean expecting the application is given it'() {
        given:
        ApplicationProbe probe = new ApplicationProbe()

        when:
        Object result = postProcessor(grailsApplication).postProcessBeforeInitialization(probe, 'probe')

        then:
        result.is(probe)
        probe.grailsApplication.is(grailsApplication)
    }

    void 'a bean expecting the configuration is given it'() {
        given:
        ConfigurationProbe probe = new ConfigurationProbe()

        when:
        postProcessor(grailsApplication).postProcessBeforeInitialization(probe, 'probe')

        then:
        probe.configuration.is(config)
    }

    void 'a bean expecting neither is left as it is'() {
        given:
        String bean = 'not aware of anything'

        expect:
        postProcessor(grailsApplication).postProcessBeforeInitialization(bean, 'probe').is(bean)
    }

    void 'a context with no application leaves the bean as it is'() {
        given: 'no GrailsApplication to hand out, as in a context that has not got one'
        ApplicationProbe probe = new ApplicationProbe()

        when:
        postProcessor(null).postProcessBeforeInitialization(probe, 'probe')

        then:
        probe.grailsApplication == null
    }

    private StandaloneGrailsApplicationAwareBeanPostProcessor postProcessor(GrailsApplication application) {
        ObjectProvider<GrailsApplication> provider = Mock(ObjectProvider) {
            getIfAvailable() >> application
        }
        new StandaloneGrailsApplicationAwareBeanPostProcessor(provider)
    }

    static class ApplicationProbe implements GrailsApplicationAware {

        GrailsApplication grailsApplication

    }

    static class ConfigurationProbe implements GrailsConfigurationAware {

        Config configuration

        @Override
        void setConfiguration(Config configuration) {
            this.configuration = configuration
        }

    }

}
