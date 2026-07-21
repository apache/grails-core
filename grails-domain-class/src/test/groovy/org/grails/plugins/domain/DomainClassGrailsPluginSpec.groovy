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

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import org.grails.config.PropertySourcesConfig
import org.grails.datastore.mapping.config.Settings as DatastoreSettings

import spock.lang.Specification

class DomainClassGrailsPluginSpec extends Specification {

    void "beanRegistrar defaults the auto-timestamp annotation cache setting when not configured"() {
        given:
        def application = new DefaultGrailsApplication()
        application.config = new PropertySourcesConfig()
        def plugin = new DomainClassGrailsPlugin(grailsApplication: application)

        when:
        applyRegistrar(plugin)

        then: 'outside development mode annotation caching stays enabled'
        application.config.getProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, Boolean) == true
    }

    void "beanRegistrar leaves an explicitly configured auto-timestamp cache setting alone"() {
        given:
        def application = new DefaultGrailsApplication()
        application.config = new PropertySourcesConfig(
                (DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS): false)
        def plugin = new DomainClassGrailsPlugin(grailsApplication: application)

        when:
        applyRegistrar(plugin)

        then:
        application.config.getProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, Boolean) == false
    }

    private static void applyRegistrar(DomainClassGrailsPlugin plugin) {
        def registrar = plugin.beanRegistrar()
        def beanFactory = new DefaultListableBeanFactory()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }
}
