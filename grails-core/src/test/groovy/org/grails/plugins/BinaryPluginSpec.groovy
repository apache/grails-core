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
import org.grails.plugins.BinaryGrailsPlugin
import org.grails.plugins.BinaryGrailsPluginDescriptor
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

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
            def descriptor = new BinaryGrailsPluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'])
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
            binaryPlugin.version == "1.0"
            binaryPlugin.providedArtefacts.size() == 1
            binaryPlugin.providedArtefacts[0] == TestBinaryResource
            binaryPlugin.binaryDescriptor != null
    }

    def "Test load static resource from binary plugin"() {
        when:
            def resource = new MockBinaryPluginResource(testBinary.getBytes('UTF-8'))
            def descriptor = new BinaryGrailsPluginDescriptor(resource, ['org.grails.plugins.TestBinaryResource'])
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

    def "Test plugin with both plugin.yml and plugin.groovy throws exception"() {
        when:
        def descriptor = new BinaryGrailsPluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'])
        MockConfigBinaryGrailsPlugin.YAML_EXISTS = true
        MockConfigBinaryGrailsPlugin.GROOVY_EXISTS = true
        new MockConfigBinaryGrailsPlugin(descriptor)

        then:
        thrown(RuntimeException)
    }

    def "Test plugin with only plugin.yml"() {
        when:
        def descriptor = new BinaryGrailsPluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'])
        MockConfigBinaryGrailsPlugin.YAML_EXISTS = true
        MockConfigBinaryGrailsPlugin.GROOVY_EXISTS = false
        def binaryPlugin = new MockConfigBinaryGrailsPlugin(descriptor)

        then:
        binaryPlugin.propertySource.getProperty('foo') == "bar"
    }

    def "Test plugin with only plugin.groovy"() {
        when:
        def descriptor = new BinaryGrailsPluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'])
        MockConfigBinaryGrailsPlugin.YAML_EXISTS = false
        MockConfigBinaryGrailsPlugin.GROOVY_EXISTS = true
        def binaryPlugin = new MockConfigBinaryGrailsPlugin(descriptor)

        then:
        binaryPlugin.propertySource.getProperty('bar') == "foo"
    }

}

class MockConfigBinaryGrailsPlugin extends BinaryGrailsPlugin {
    static Boolean YAML_EXISTS = false
    static Boolean GROOVY_EXISTS = false

    MockConfigBinaryGrailsPlugin(BinaryGrailsPluginDescriptor descriptor) {
        super(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())
    }

    protected Resource getConfigurationResource(Class<?> pluginClass, String path) {
        File tempDir = Files.createTempDirectory("MockConfigBinaryGrailsPlugin").toFile()
        if (YAML_EXISTS && path == PLUGIN_YML_PATH) {
            File file = new File(tempDir, "plugin.yml")
            file.write("foo: bar")
            return new FileSystemResource(file)
        }
        if (GROOVY_EXISTS && path == PLUGIN_GROOVY_PATH) {
            File file = new File(tempDir, "plugin.groovy")
            file.write("bar = 'foo'")
            return new FileSystemResource(file)
        }
        return null
    }

    public String getVersion() {
        super.getVersion()
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

