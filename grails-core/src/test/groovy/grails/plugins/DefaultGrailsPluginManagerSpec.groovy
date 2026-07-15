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
package grails.plugins

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginInfo
import org.apache.grails.core.plugins.PluginDiscovery
import org.apache.grails.core.plugins.PluginUtils
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test suite for DefaultGrailsPluginManager
 */
class DefaultGrailsPluginManagerSpec extends Specification {

    def "plugin manager can be created with application and discovery bean"() {
        given:
        def app = Mock(GrailsApplication)
        def discovery = Mock(PluginDiscovery)

        when:
        def manager = new DefaultGrailsPluginManager(app, discovery)

        then:
        manager != null
    }

    def "plugin manager can be created with just application"() {
        given:
        def app = Mock(GrailsApplication)
        def discovery = Mock(PluginDiscovery)

        when:
        def manager = new DefaultGrailsPluginManager(app, discovery)

        then:
        manager != null
    }

    @Unroll
    def "should return #pluginGrailsVersion as plugin grails version"() {
        given:
        PluginInfo plugin = stubPluginWithGrailsVersion(pluginGrailsVersion)

        when:
        def version = plugin.getGrailsVersionRange()

        then:
        version == pluginGrailsVersion

        where:
        pluginGrailsVersion | _
        "3.3.10 > *"        | _
    }

    @Unroll
    def "it should check that plugin with grailsVersion=#pluginGrailsVersion is compatible with grails #grailsVersion"() {
        given:
        PluginInfo plugin = stubPluginWithGrailsVersion(pluginGrailsVersion)

        when:
        def compatible = plugin.isGrailsVersionCompatible(grailsVersion)

        then:
        compatible == expectedCompatible

        where:
        grailsVersion    | pluginGrailsVersion        || expectedCompatible
        "1.0"            | "3.3.1 > *"                || false
        "2.5"            | "3.0.1"                    || false
        "3.0.0"          | "3.3.10 > *"               || false
        "3.3.10"         | "4.0.0 > *"                || false
        "4.0.1"          | "3.0.0.BUILD-SNAPSHOT > *" || true
        "4.0.1"          | "4.0.1"                    || true
        "4.0.1"          | "3.0.1"                    || false
        "4.0.1"          | "3.3.1 > *"                || true
        "4.0.1"          | "3.3.10 > *"               || true

        // Milestone, release candidate and snapshot versions on both the application and the plugin (#14058)
        "7.0.0-M2"       | "7.0.0-M1 > *"             || true
        "7.0.0-M1"       | "7.0.0-M2 > *"             || false
        "7.0.0-RC1"      | "7.0.0-M1 > *"             || true
        "7.0.0-M1"       | "7.0.0-RC1 > *"            || false
        "7.0.0"          | "7.0.0-RC1 > *"            || true
        "7.0.0-RC1"      | "7.0.0 > *"                || false
        "7.0.0-SNAPSHOT" | "7.0.0-SNAPSHOT > *"       || true
        "7.0.5-M1"       | "7.0.3 > *"                || true
        "7.0.0-M1"       | "7.0.0-M1"                 || true
        "7.0.0-M2"       | "7.0.0-M1"                 || false
    }

    def "per-plugin loaded messages are DEBUG and a single INFO summary reports the load order"() {
        given: 'a discovery bean with two plugins registered in reverse of their load order'
        def gcl = new GroovyClassLoader()
        def alphaClass = gcl.parseClass('''
class AlphaProbeGrailsPlugin {
    def version = "1.0.0"
}
''')
        def betaClass = gcl.parseClass('''
class BetaProbeGrailsPlugin {
    def version = "2.0.0"
    def loadAfter = ['alphaProbe']
}
''')
        def application = new DefaultGrailsApplication()
        application.mainContext = new GenericApplicationContext()
        def discovery = new DefaultPluginDiscovery(new Class<?>[]{betaClass, alphaClass})
        discovery.loadPluginsFromClasspath = false

        and: 'standard error is captured to observe slf4j-simple output'
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when:
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(application, discovery)
        manager.loadPlugins()

        then: 'both plugins are loaded'
        manager.getGrailsPlugin('alphaProbe') != null
        manager.getGrailsPlugin('betaProbe') != null

        and: 'the per-plugin loaded-successfully messages are not emitted at INFO'
        captured.toString().readLines()
                .findAll { it.contains('loaded successfully') }
                .every { !it.contains('INFO') }

        and: 'a single INFO summary line reports the plugins in load order'
        def summaryLines = captured.toString().readLines()
                .findAll { it.contains('Grails plugins in load order') }
        summaryLines.size() == 1
        summaryLines[0].contains('INFO')
        summaryLines[0].contains('Loaded 2 Grails plugins in load order: [alphaProbe (1.0.0), betaProbe (2.0.0)]')

        cleanup:
        System.setErr(originalErr)
    }

    PluginInfo stubPluginWithGrailsVersion(String grailsVersion) {
        def gcl = new GroovyClassLoader()
        return PluginUtils.createPluginInfo(gcl.parseClass("class ACustomGrailsPlugin {\n" +
                "def version = \"1.0.0\"\n" +
                "def grailsVersion = \"$grailsVersion\"\n" +
                "}"), null, true)
    }
}
