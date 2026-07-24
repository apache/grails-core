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
package grails.plugin.springsecurity

import groovy.json.JsonSlurper
import spock.lang.Specification

class ConfigurationMetadataSpec extends Specification {

    void 'generates merged configuration metadata from DefaultSecurityConfig'() {
        given:
        Map metadata = configurationMetadata()
        Map<String, Map> properties = metadata.get('properties').collectEntries { Map property -> [(property.get('name')): property] }

        expect: 'the complete curated metadata contract is retained'
        metadata.get('groups').size() == 14
        properties.size() == 145

        and: 'literal DSL defaults are present'
        properties['grails.plugin.springsecurity.active'].type == 'java.lang.Boolean'
        properties['grails.plugin.springsecurity.active'].defaultValue == true

        and: 'conditional DSL properties retain curated defaults'
        properties['grails.plugin.springsecurity.password.bcrypt.logrounds'].type == 'java.lang.Integer'
        properties['grails.plugin.springsecurity.password.bcrypt.logrounds'].defaultValue == 10

        and: 'nested DSL paths are mapped beneath the Spring Security prefix'
        properties['grails.plugin.springsecurity.userLookup.usernamePropertyName'].type == 'java.lang.String'
        properties['grails.plugin.springsecurity.userLookup.usernamePropertyName'].defaultValue == 'username'

        and: 'curated metadata augments statically discovered values'
        properties['grails.plugin.springsecurity.beanTypeResolverClass'].type == 'java.lang.String'
        properties['grails.plugin.springsecurity.beanTypeResolverClass'].description == 'Bean type resolver class name.'
    }

    private static Map configurationMetadata() {
        List<URL> resources = ConfigurationMetadataSpec.classLoader
                .getResources('META-INF/spring-configuration-metadata.json')
                .findAll { URL candidate ->
                    Map metadata = new JsonSlurper().parse(candidate) as Map
                    (metadata.get('groups') ?: []).any { Map group -> group.get('name') == 'grails.plugin.springsecurity' }
                }
        assert resources.size() == 1
        new JsonSlurper().parse(resources.first()) as Map
    }
}
