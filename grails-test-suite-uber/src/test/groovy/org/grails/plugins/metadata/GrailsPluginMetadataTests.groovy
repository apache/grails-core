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

import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.plugins.metadata.GrailsPlugin
import grails.util.GrailsUtil
import org.apache.grails.core.plugins.DefaultPluginDiscovery

class GrailsPluginMetadataTests extends Specification {

    @Shared GrailsPluginManager pluginManager

    void setupSpec() {
        def app = new DefaultGrailsApplication([Test1, Test2, Test3] as Class[], getClass().classLoader)
        def discovery = new DefaultPluginDiscovery().tap {
            init(new StandardEnvironment())
        }
        pluginManager = new DefaultGrailsPluginManager(app, discovery).tap {
            loadPlugins()
        }
    }

    void 'returns plugins for instances'(Object instance, String name) {
        given:
        def plugin = pluginManager.getPluginForInstance(instance)

        expect:
        plugin?.name == name

        where:
        instance    | name
        new Test1() | 'controllers'
        new Test2() | 'dataBinding'
        new Test3() | null
    }

    void 'returns plugins for classes'(Class clazz, String name) {
        given:
        def plugin = pluginManager.getPluginForClass(clazz)

        expect:
        plugin?.name == name

        where:
        clazz | name
        Test1 | 'controllers'
        Test2 | 'dataBinding'
        Test3 | null
    }

    void 'returns plugin path for instances'(Object instance, String expectedPath) {
        given:
        def path = pluginManager.getPluginPathForInstance(instance)

        expect:
        path == expectedPath

        where:
        instance    | expectedPath
        new Test1() | "/plugins/controllers-$GrailsUtil.grailsVersion"
        new Test2() | "/plugins/data-binding-$GrailsUtil.grailsVersion"
        new Test3() | null
    }

    void 'returns plugin path for classes'(Class clazz, String expectedPath) {
        given:
        def path = pluginManager.getPluginPathForClass(clazz)

        expect:
        path == expectedPath

        where:
        clazz | expectedPath
        Test1 | "/plugins/controllers-$GrailsUtil.grailsVersion"
        Test2 | "/plugins/data-binding-$GrailsUtil.grailsVersion"
        Test3 | null
    }

    void 'returns plugin views path for instances'(Object instance, String expectedPath) {
        given:
        def viewsPath = pluginManager.getPluginViewsPathForInstance(instance)

        expect:
        viewsPath == expectedPath

        where:
        instance    | expectedPath
        new Test1() | "/plugins/controllers-$GrailsUtil.grailsVersion/grails-app/views"
        new Test2() | "/plugins/data-binding-$GrailsUtil.grailsVersion/grails-app/views"
        new Test3() | null
    }

    void 'returns plugin views path for classes'(Class clazz, String expectedPath) {
        given:
        def viewsPath = pluginManager.getPluginViewsPathForClass(clazz)

        expect:
        viewsPath == expectedPath

        where:
        clazz | expectedPath
        Test1 | "/plugins/controllers-$GrailsUtil.grailsVersion/grails-app/views"
        Test2 | "/plugins/data-binding-$GrailsUtil.grailsVersion/grails-app/views"
        Test3 | null
    }
}

@GrailsPlugin(name='controllers', version='1.0')
class Test1 {}
@GrailsPlugin(name='dataBinding', version='1.2')
class Test2 {}
class Test3 {}
