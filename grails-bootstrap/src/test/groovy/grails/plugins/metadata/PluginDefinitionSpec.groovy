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
package grails.plugins.metadata

import spock.lang.Specification

class PluginDefinitionSpec extends Specification {

    void 'a declaration and its published form give consumers the same resolved constraints'() {
        given:
        def definition = new PluginDefinition.Builder(
                pluginName: 'data-source', pluginClass: 'example.DataSourceGrailsPlugin', pluginVersion: '9.0.1',
                grailsVersionRange: '${plugin.version} > *',
                dependsOn: [core: '${plugin.version}'], loadAfter: ['some-plugin']
        ).build()
        def output = new StringWriter()

        when:
        definition.write(output)
        def published = PluginDefinition.read('dataSource', new StringReader(output.toString()))

        then:
        published == definition
        published.dependsOn == [core: '9.0.1']
        published.grailsVersionRange == '9.0.1 > *'
        published.loadAfter == ['somePlugin']
    }

    void 'tools read immutable metadata without loading the implementation or its artefacts'() {
        when:
        def definition = PluginDefinition.read('catalog-probe', new StringReader('''
            name=catalogProbe
            class=missing.CatalogPlugin
            version=3.7
            grailsVersion=9.0 > *
            dependsOn.other-plugin=2.0 > *
            artefacts.Service=missing.ExampleService
        '''))

        then:
        definition.pluginName == 'catalogProbe'
        definition.pluginClass == 'missing.CatalogPlugin'
        definition.pluginVersion == '3.7'
        definition.providedClasses == ['missing.ExampleService']
        definition.dependsOn == [otherPlugin: '2.0 > *']
        definition.grailsVersionRange == '9.0 > *'

        when:
        definition.artefactsByType.Service.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    void 'invalid published metadata identifies the erroneous field'() {
        when:
        PluginDefinition.read('probe', new StringReader(line))

        then:
        def error = thrown(IllegalArgumentException)
        error.message.contains(field)

        where:
        line                      | field
        'format.version=2'        | 'format'
        'enabled=nope'            | 'enabled'
        'name=probe'              | 'names no plugin class'
        'name=mistake'            | 'name'
    }

    void 'pluginExcludes are patterns of artefact class names'() {
        when:
        def definition = new PluginDefinition.Builder(
                pluginName: 'probe', pluginClass: 'example.ProbeGrailsPlugin',
                pluginExcludes: ['com/example/demo/**', '**/testapp/**']).build()

        then:
        definition.pluginExcludes == ['com/example/demo/**', '**/testapp/**']
    }

    void 'a pluginExcludes pattern of source files is rejected, because it can never match a class: #pattern'() {
        when:
        new PluginDefinition.Builder(
                pluginName: 'probe', pluginClass: 'example.ProbeGrailsPlugin', pluginExcludes: [pattern]).build()

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(pattern)
        e.message.contains('class names')

        where:
        pattern << ['grails-app/views/error.gsp', 'grails-app/views/**', 'src/templates/**', '**/*.gsp', 'codenarc.properties']
    }
}
