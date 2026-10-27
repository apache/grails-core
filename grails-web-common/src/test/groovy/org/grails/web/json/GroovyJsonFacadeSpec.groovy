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
package org.grails.web.json

import spock.lang.Specification

class GroovyJsonFacadeSpec extends Specification {

    void 'parse returns existing JSONObject facade backed by groovy-json parsing'() {
        when:
        JSONElement element = GroovyJsonFacade.parse('{"name":"Grails","versions":[8,9]}')

        then:
        element instanceof JSONObject
        element.get('name') == 'Grails'
        element.getJSONArray('versions').getInt(0) == 8
    }

    void 'toJson renders existing JSONObject facade through groovy-json'() {
        given:
        JSONObject object = new JSONObject()
        object.put('name', 'Grails')
        object.put('active', true)

        expect:
        GroovyJsonFacade.toJson(object) == '{"name":"Grails","active":true}'
    }
}
