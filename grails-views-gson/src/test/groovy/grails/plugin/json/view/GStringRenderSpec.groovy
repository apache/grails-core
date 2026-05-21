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
package grails.plugin.json.view

import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Specification

/**
 * Created by graemerocher on 27/09/2016.
 */
class GStringRenderSpec extends Specification implements JsonViewTest {

    void "Test render an exception type"() {

        when:"An exception is rendered"
        def renderResult = render('''
model {
    String value = 'abc'
}

json {
    example1 "abc"
    example2 "${value}"
    example3 "$value"
    example4 "$value".toString()
}
''')

        then:"The exception is rendered"
        renderResult.json.example1 == 'abc'
        renderResult.json.example2 == 'abc'
        renderResult.json.example3 == 'abc'
        renderResult.json.example4 == 'abc'

    }

}
