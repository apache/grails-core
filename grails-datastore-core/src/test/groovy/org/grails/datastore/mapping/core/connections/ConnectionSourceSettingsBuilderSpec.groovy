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
package org.grails.datastore.mapping.core.connections

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

class ConnectionSourceSettingsBuilderSpec extends Specification {

    void "portable identity type is long by default"() {
        expect:
        buildSettings([:]).defaultIdType == 'long'
    }

    void "portable identity type is read from GORM runtime configuration"() {
        expect:
        buildSettings(['grails.gorm.defaultIdType': 'native']).defaultIdType == 'native'
    }

    private static ConnectionSourceSettings buildSettings(Map<String, Object> values) {
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', values))
        new ConnectionSourceSettingsBuilder(environment).build()
    }
}
