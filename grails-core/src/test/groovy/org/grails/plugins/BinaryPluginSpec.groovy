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

import grails.core.DefaultGrailsApplication
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.core.plugins.PluginDescriptor

class BinaryPluginSpec extends Specification {

    @Shared
    String testBinary = '''
    <plugin name='testBinary'>
      <class>org.grails.plugins.TestBinaryGrailsPlugin</class>
      <resources>
             <resource>org.grails.plugins.TestBinaryResource</resource>
      </resources>
    </plugin>
    '''

    def "Test creation of a binary plugin"() {
        when:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], ['org.grails.plugins.TestBinaryResource'])
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
            binaryPlugin.version == "1.0"
            binaryPlugin.providedArtefacts.size() == 1
            binaryPlugin.providedArtefacts[0] == TestBinaryResource
    }


    def "Test load static resource from binary plugin"() {
        when:
            def resource = new MockBinaryPluginResource(testBinary.getBytes('UTF-8'))
        def descriptor = new PluginDescriptor(resource, ['org.grails.plugins.TestBinaryResource'], [])
            resource.relativesResources['static/css/main.css'] = new ByteArrayResource(''.bytes)
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())
            def cssResource = binaryPlugin.getResource("/css/main.css")

        then:
            cssResource != null
        when:
            cssResource = binaryPlugin.resolveView("/css/foo.css")

        then:
            cssResource == null
    }

    def "Test getPropertySource returns null when no mainContext is set on GrailsApplication"() {
        when:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
        binaryPlugin.propertySource == null
    }

    def "Test getPropertySource looks up plugin.yml from environment"() {
        given:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        def grailsApp = new DefaultGrailsApplication()
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, grailsApp)

        def appCtx = new GenericApplicationContext()
        def ymlPropertySource = new MapPropertySource('testBinary-plugin.yml', [foo: 'bar'])
        appCtx.environment.propertySources.addLast(ymlPropertySource)

        when:
        grailsApp.mainContext = appCtx

        then:
        binaryPlugin.propertySource != null
        binaryPlugin.propertySource.getProperty('foo') == 'bar'
    }

    def "Test getPropertySource looks up plugin.groovy from environment"() {
        given:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        def grailsApp = new DefaultGrailsApplication()
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, grailsApp)

        def appCtx = new GenericApplicationContext()
        def groovyPropertySource = new MapPropertySource('testBinary-plugin.groovy', [bar: 'foo'])
        appCtx.environment.propertySources.addLast(groovyPropertySource)

        when:
        grailsApp.mainContext = appCtx

        then:
        binaryPlugin.propertySource != null
        binaryPlugin.propertySource.getProperty('bar') == 'foo'
    }

    def "Test mutual exclusion of plugin.yml and plugin.groovy is enforced by EPP"() {
        expect: "Constructor no longer throws when both config files exist - validation moved to GrailsEnvironmentPostProcessor"
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication()) != null
    }

}

class TestBinaryGrailsPlugin {
    def version = 1.0
}

class TestBinaryResource {}

class MockBinaryPluginResource extends ByteArrayResource {

    Map<String, Resource> relativesResources = [:]

    MockBinaryPluginResource(byte[] byteArray) {
        super(byteArray)
    }

    @Override
    Resource createRelative(String relativePath) {
        return relativesResources[relativePath]
    }
}

class MyView extends Script {
    @Override
    Object run() {
        return "Good"
    }
}

