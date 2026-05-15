/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.core.io

import spock.lang.Specification

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ResourceLoader

import grails.core.DefaultGrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.apache.grails.core.plugins.PluginDescriptor
import org.grails.plugins.BinaryGrailsPlugin
import org.grails.plugins.MockBinaryPluginResource
import org.grails.plugins.TestBinaryGrailsPlugin

class ResourceLocatorSpec extends Specification {

    void 'finds a resource for a simple URI and returns null for a missing URI'() {
        given: 'a resource locator backed by a mock resource loader'
        def loader = new MockStringResourceLoader().tap {
            registerMockResource('file:./web-app/css/main.css', 'dummy contents')
        }
        def resourceLocator = new MockResourceLocator(defaultResourceLoader: loader).tap {
            searchLocation = './'
        }

        when: 'an existing URI is resolved'
        def resource = resourceLocator.findResourceForURI('/css/main.css')

        then: 'the matching resource is returned'
        resource

        when: 'a missing URI is resolved'
        resource = resourceLocator.findResourceForURI('/css/notThere.css')

        then: 'no resource is returned'
        !resource
    }

    void 'finds a static resource contributed by a binary plugin'() {
        given: 'a resource locator configured with a plugin manager exposing a binary plugin'
        def resourceLocator = new MockResourceLocator(defaultResourceLoader: new MockStringResourceLoader())
        def binaryPlugin = binaryPlugin

        and: 'a plugin manager that returns the binary plugin'
        def manager = Mock(GrailsPluginManager)
        manager.allPlugins >> ([binaryPlugin] as GrailsPlugin[])
        resourceLocator.pluginManager = manager

        when: 'a binary plugin URI is resolved'
        def resource = resourceLocator.findResourceForURI('/plugins/test-binary-1.0/css/main.css')

        then: 'the plugin resource is returned'
        resource
    }

    private static BinaryGrailsPlugin getBinaryPlugin() {
        def pluginXml = '''
            <plugin name='testBinary'>
                <class>org.grails.plugins.TestBinaryGrailsPlugin</class>
            </plugin>
        '''
        def resource = new MockBinaryPluginResource(pluginXml.bytes).tap {
            relativesResources['static/css/main.css'] = new ByteArrayResource(''.bytes)
        }
        new BinaryGrailsPlugin(
                TestBinaryGrailsPlugin,
                new PluginDescriptor(resource, ['org.grails.plugins.TestBinaryGrailsPlugin'], []),
                new DefaultGrailsApplication().tap {
                    mainContext = new GenericApplicationContext()
                }
        )
    }
}

class MockResourceLocator extends DefaultResourceLocator {

    ResourceLoader defaultResourceLoader
}
