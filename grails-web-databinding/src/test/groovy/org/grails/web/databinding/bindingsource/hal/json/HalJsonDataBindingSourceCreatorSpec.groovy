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
package org.grails.web.databinding.bindingsource.hal.json

import org.grails.web.databinding.bindingsource.HalJsonDataBindingSourceCreator

import spock.lang.Specification

class HalJsonDataBindingSourceCreatorSpec extends Specification {

    void 'Test JSON parsing'() {
        given:
        def json = '''
            {
    "name": "Douglas",
    "age": "42",
    "_embedded" : {
        "homeAddress" : { "state": "Missouri", "city": "O'Fallon"},
        "workAddress" : { "state": "California", "city": "San Mateo"}
    }
}
'''
        def inputStream = new ByteArrayInputStream(json.getBytes("UTF-8"))
        def bindingSource = new HalJsonDataBindingSourceCreator().createBindingSource(inputStream, "UTF-8")

        when:
        def propertyNames = bindingSource.propertyNames

        then:
        propertyNames.contains 'age'
        propertyNames.contains 'name'
        propertyNames.contains 'homeAddress'
        propertyNames.contains 'workAddress'
        bindingSource.containsProperty 'name'
        bindingSource.containsProperty 'age'
        bindingSource.containsProperty 'homeAddress'
        bindingSource.containsProperty 'workAddress'

        bindingSource['name'] == 'Douglas'
        bindingSource['age'] == '42'
        bindingSource['homeAddress']['state'] == 'Missouri'
        bindingSource['homeAddress']['city'] == "O'Fallon"
        bindingSource['workAddress']['state'] == 'California'
        bindingSource['workAddress']['city'] == 'San Mateo'
    }
}
