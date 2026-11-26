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
package org.grails.plugins.metadata

import spock.lang.Shared
import spock.lang.Specification

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.DefaultGrailsClass
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.util.GrailsUtil
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.grails.plugins.codecs.HTMLCodec

/**
 * The plugin a class belongs to is the plugin whose descriptor lists it among its artefacts.
 */
class GrailsPluginMetadataTests extends Specification {

    @Shared GrailsPluginManager pluginManager

    void setupSpec() {
        def app = new DefaultGrailsApplication([NotAnArtefact] as Class[], getClass().classLoader)
        def discovery = new DefaultPluginDiscovery().tap {
            init(new StandardEnvironment())
        }
        pluginManager = new DefaultGrailsPluginManager(app, discovery).tap {
            loadPlugins()
        }
    }

    void 'an artefact the codecs plugin descriptor lists belongs to the codecs plugin'() {
        expect:
        pluginManager.getPluginForClass(HTMLCodec).name == 'codecs'
        pluginManager.getPluginForInstance(new HTMLCodec()).name == 'codecs'
    }

    void 'a class no descriptor lists belongs to no plugin'() {
        expect:
        pluginManager.getPluginForClass(NotAnArtefact) == null
        pluginManager.getPluginForInstance(new NotAnArtefact()) == null
        pluginManager.getPluginPathForClass(NotAnArtefact) == null
        pluginManager.getPluginViewsPathForClass(NotAnArtefact) == null
    }

    void 'the plugin paths of an artefact are those of the plugin that provides it'() {
        expect:
        pluginManager.getPluginPathForClass(HTMLCodec) == "/plugins/codecs-$GrailsUtil.grailsVersion"
        pluginManager.getPluginPathForInstance(new HTMLCodec()) == "/plugins/codecs-$GrailsUtil.grailsVersion"
        pluginManager.getPluginViewsPathForClass(HTMLCodec) == "/plugins/codecs-$GrailsUtil.grailsVersion/grails-app/views"
        pluginManager.getPluginViewsPathForInstance(new HTMLCodec()) == "/plugins/codecs-$GrailsUtil.grailsVersion/grails-app/views"
    }

    void 'a Grails class of an application names the plugin that provides it, through the application\'s plugin manager'() {
        given: 'an application whose context holds the plugin manager, and nothing in Holders'
        GrailsPluginManager previous = Holders.pluginManager
        Holders.pluginManager = null
        def context = new GenericApplicationContext()
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
        context.refresh()
        def application = new DefaultGrailsApplication()
        application.mainContext = context
        def codec = new DefaultGrailsClass(HTMLCodec, 'Codec')
        codec.grailsApplication = application

        expect:
        codec.pluginName == 'codecs'

        cleanup:
        context?.close()
        Holders.pluginManager = previous
    }

    void 'a Grails class names the plugin that provides it'() {
        given:
        GrailsPluginManager previous = Holders.pluginManager
        Holders.pluginManager = pluginManager

        expect:
        new DefaultGrailsClass(HTMLCodec, 'Codec').pluginName == 'codecs'
        new DefaultGrailsClass(NotAnArtefact, '').pluginName == null

        cleanup:
        Holders.pluginManager = previous
    }
}

class NotAnArtefact {}
