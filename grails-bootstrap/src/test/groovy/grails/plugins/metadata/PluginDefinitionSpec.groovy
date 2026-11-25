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

    void 'tools read immutable metadata without loading the implementation or its artefacts'() {
        when:
        def definition = PluginDefinition.read('catalog-probe', new StringReader('''
            name=catalogProbe
            class=missing.CatalogPlugin
            version=3.7
            grailsVersion=9.0 > *
            dependsOn.other-plugin=2.0 > *
            artefacts.Service=missing.ExampleService
            metadata.complete=true
        '''))

        then:
        definition.pluginName == 'catalogProbe'
        definition.providedPlugins == ['missing.CatalogPlugin']
        definition.dependsOn == [otherPlugin: '2.0 > *']
        definition.grailsVersionRange == '9.0 > *'
        definition.metadataComplete

        when:
        definition.providedArtefactsByType.Service.clear()

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
        'metadata.complete=nope'  | 'metadata.complete'
        'name=mistake'            | 'name'
    }
}
