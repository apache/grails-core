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
package org.grails.core.io

import groovy.xml.XmlSlurper
import grails.core.DefaultGrailsApplication
import groovy.xml.XmlSlurper
import org.grails.plugins.*
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification

class ResourceLocatorSpec extends Specification {

    void "test find simple URI"() {
        given: "Resource locator with mock resource loader"
            def loader = new MockStringResourceLoader()
            loader.registerMockResource("file:./web-app/css/main.css", "dummy contents")
            def resourceLocator = new MockResourceLocator(defaultResourceLoader: loader)
            resourceLocator.searchLocation = "./"

        when: "An existing resource is queried"
            def res = resourceLocator.findResourceForURI("/css/main.css")

        then: "Make sure it is found"
            assert res != null

        when: "A non-existent resource is queried"
            res = resourceLocator.findResourceForURI("/css/notThere.css")

        then: "null is returned"
            res == null
    }

    void "test find resource from binary plugin"() {
        given: "Resource locator with mock resource loader and a plugin manager"
             def loader = new MockStringResourceLoader()
             def resourceLocator = new MockResourceLocator(defaultResourceLoader: loader)
             def manager = new MockGrailsPluginManager()
             manager.registerMockPlugin(getBinaryPlugin())
             resourceLocator.pluginManager = manager

        when: "A binary plugin resource is queried"
            def res = resourceLocator.findResourceForURI("/plugins/test-binary-1.0/css/main.css")

        then: "The resource is found"
            assert res != null
    }

    BinaryGrailsPlugin getBinaryPlugin() {
            def str = '''
    <plugin name='testBinary'>
      <class>org.grails.plugins.TestBinaryGrailsPlugin</class>
    </plugin>
    '''

            def xml = new XmlSlurper().parseText(str)

            def resource = new MockBinaryPluginResource(str.bytes)
            def descriptor = new BinaryGrailsPluginDescriptor(resource, ['org.grails.plugins.TestBinaryGrailsPlugin'])
            resource.relativesResources['static/css/main.css'] = new ByteArrayResource(''.bytes)
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())
    }
}

class MockResourceLocator extends DefaultResourceLocator {
    ResourceLoader defaultResourceLoader
}
