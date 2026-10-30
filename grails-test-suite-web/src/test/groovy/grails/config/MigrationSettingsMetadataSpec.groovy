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
package grails.config

import groovy.json.JsonSlurper

import spock.lang.Specification

/**
 * The settings whose defaults later releases change are read with the defaults {@link Settings} gives them, which the
 * configuration metadata of the modules that read them advertises.
 */
class MigrationSettingsMetadataSpec extends Specification {

    void 'the configuration metadata gives #name the default Grails reads it with'() {
        expect:
        metadataDefaults().get(name) == Boolean.parseBoolean(defaultValue)

        where:
        name                                | defaultValue
        Settings.WEB_RENDERING_JSON_SPRING  | Settings.WEB_RENDERING_JSON_SPRING_DEFAULT
        Settings.WEB_RENDERING_XML_SPRING   | Settings.WEB_RENDERING_XML_SPRING_DEFAULT
        Settings.DATABINDING_JSON_JACKSON   | Settings.DATABINDING_JSON_JACKSON_DEFAULT
        Settings.SETTING_EXCEPTION_RESOLVER_SPRING_HANDLERS | Settings.SETTING_EXCEPTION_RESOLVER_SPRING_HANDLERS_DEFAULT
        Settings.CONTROLLERS_RESPONSE_BODY  | Settings.CONTROLLERS_RESPONSE_BODY_DEFAULT
    }

    private static Map<String, Object> metadataDefaults() {
        Map<String, Object> defaults = [:]
        MigrationSettingsMetadataSpec.classLoader.getResources('META-INF/additional-spring-configuration-metadata.json').each { URL url ->
            Map metadata = (Map) new JsonSlurper().parse(url)
            metadata['properties'].each { Map property -> defaults.put((String) property.name, property.defaultValue) }
        }
        defaults
    }
}
