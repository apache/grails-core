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

package org.grails.plugins.domain

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.util.Environment as GrailsEnvironment

import org.grails.datastore.mapping.config.Settings as DatastoreSettings

import spock.lang.Specification

/**
 * The auto-timestamp annotation-caching default is read by
 * {@code AutoTimestampEventListener} and {@code DomainModelServiceImpl} through a
 * {@code @Value("${...}")} placeholder, so it has to reach the environment.
 */
class AutoTimestampCacheDefaultSpec extends Specification {

    void 'the default is contributed to the environment, keyed on development mode'() {
        given:
        StandardEnvironment environment = new StandardEnvironment()

        expect: 'nothing is configured to begin with'
        environment.getProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS) == null

        when:
        new DomainClassEnvironmentPostProcessor().postProcessEnvironment(environment, null)

        then:
        environment.getProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, Boolean) ==
                !GrailsEnvironment.isDevelopmentMode()
    }

    void 'an explicitly configured value is left alone'() {
        given:
        StandardEnvironment environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test',
                [(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS): 'false']))

        when:
        new DomainClassEnvironmentPostProcessor().postProcessEnvironment(environment, null)

        then:
        !environment.getProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, Boolean)
    }

    void 'the contributed default resolves through a property placeholder'() {
        given: 'the environment the post-processor has run against'
        GenericApplicationContext context = new GenericApplicationContext()
        new DomainClassEnvironmentPostProcessor().postProcessEnvironment(context.environment, null)

        and: 'a bean reading the setting the way the real consumers do'
        RootBeanDefinition probe = new RootBeanDefinition(Probe)
        probe.propertyValues.add('value',
                '${' + DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS + ':unset}')
        context.registerBeanDefinition('probe', probe)
        context.registerBeanDefinition('placeholderConfigurer',
                new RootBeanDefinition(org.springframework.context.support.PropertySourcesPlaceholderConfigurer))

        when:
        context.refresh()

        then: 'it sees the contributed default rather than falling back'
        context.getBean(Probe).value == String.valueOf(!GrailsEnvironment.isDevelopmentMode())

        cleanup:
        context.close()
    }

    static class Probe {

        String value

    }

}
