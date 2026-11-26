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
package org.grails.plugins

import spock.lang.Specification

import grails.core.DefaultGrailsApplication

/**
 * The plugin a class belongs to is the plugin whose descriptor lists it, looked up through an index the plugin
 * manager keeps in step with the plugins it has loaded.
 */
class PluginArtefactOwnershipSpec extends Specification {

    void 'a class belongs to the plugin that provides it, including one registered after an earlier lookup'() {
        given: 'two plugins, compiled at test time, each providing one artefact'
        def loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass('''
            package shop
            class Basket {}
            class Till {}
            @grails.plugins.metadata.GrailsPluginDescriptor(name = 'basket', providedArtefacts = [Basket])
            class BasketGrailsPlugin extends grails.plugins.Plugin {}
            @grails.plugins.metadata.GrailsPluginDescriptor(name = 'till', providedArtefacts = [Till])
            class TillGrailsPlugin extends grails.plugins.Plugin {}
        ''')
        def application = new DefaultGrailsApplication()
        def manager = new MockGrailsPluginManager(application)
        manager.registerMockPlugin(new DefaultGrailsPlugin(loader.loadClass('shop.BasketGrailsPlugin'), application))

        expect: 'the first plugin owns its artefact, and nothing owns the other plugin\'s yet'
        manager.getPluginForClass(loader.loadClass('shop.Basket')).name == 'basket'
        manager.getPluginForClass(loader.loadClass('shop.Till')) == null
        manager.getPluginForClass(String) == null

        when: 'the second plugin is registered after those lookups'
        manager.registerMockPlugin(new DefaultGrailsPlugin(loader.loadClass('shop.TillGrailsPlugin'), application))

        then: 'its artefact is found, so the index followed the change'
        manager.getPluginForClass(loader.loadClass('shop.Till')).name == 'till'
        manager.getPluginForClass(loader.loadClass('shop.Basket')).name == 'basket'
    }
}
