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
package org.grails.core.io

import spock.lang.Specification

class GrailsByteArrayResourceSpec extends Specification {

    void 'getURL wraps a URISyntaxException as an IOException naming the fake description'() {
        given: 'Spring always wraps the description as "Byte array resource [...]", which is never a valid absolute URI path'
        def resource = new GrailsByteArrayResource('hello'.bytes, 'my description')

        when:
        resource.getURL()

        then:
        def ex = thrown(IOException)
        ex.message == "Invalid fake file URL: ${resource.getDescription()}"
        ex.cause instanceof URISyntaxException
    }

    void 'getFilename returns the Spring-wrapped description'() {
        given:
        def resource = new GrailsByteArrayResource('hello'.bytes, 'my description')

        expect:
        resource.getFilename() == 'Byte array resource [my description]'
        resource.getFilename() == resource.getDescription()
    }

    void 'a resource constructed without a location uses the default description'() {
        given:
        def resource = new GrailsByteArrayResource('data'.bytes)

        expect:
        resource.getFilename() == 'Byte array resource [resource loaded from byte array]'
    }

}
