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
package org.grails.datastore.gorm.jdbc

import org.springframework.core.env.PropertySource
import spock.lang.Specification

class PropertyOriginSpec extends Specification {

    void "getSource and getName return the values passed to the constructor"() {
        given:
        def source = new PropertySource<String>('mySource', 'value') {
            @Override
            Object getProperty(String name) { null }
        }

        when:
        def origin = new PropertyOrigin(source, 'my.original.name')

        then:
        origin.source.is(source)
        origin.name == 'my.original.name'
    }

    void "source may be null"() {
        when:
        def origin = new PropertyOrigin(null, 'my.original.name')

        then:
        origin.source == null
        origin.name == 'my.original.name'
    }
}
