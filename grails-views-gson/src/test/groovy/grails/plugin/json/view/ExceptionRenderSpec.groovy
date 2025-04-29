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
 * Created by graemerocher on 30/08/2016.
 */
class ExceptionRenderSpec extends Specification implements JsonViewTest {

    void "Test render an exception type"() {

        when:"An exception is rendered"
        def renderResult = render('''
import groovy.transform.*
import grails.plugin.json.view.*

try {
    throw new RuntimeException("bad")
}
catch(Throwable e) {
    // test log the exception
    log.error "foo", e
    json g.render(e)
}

''')

        then:"The exception is rendered"
        renderResult.json.message == 'bad'
        renderResult.json.stacktrace[0] == '6 | JsonView0.run'

    }
    void "Test render an exception type with jsonapi"() {

        when:"An exception is rendered with jsonapi"
            def renderResult = render('''
        import groovy.transform.*
        import grails.plugin.json.view.*

        try {
            throw new RuntimeException("bad")
        }
        catch(Throwable e) {
            json jsonapi.render(e)
        }

        ''')

        then:"The exception is rendered"
        renderResult.jsonText.startsWith('{"errors":[{"status":500,"title":"java.lang.RuntimeException","detail":"bad","source":{"stacktrace"')
        renderResult.json.errors[0].source.stacktrace[0] == '6 | JsonView0.run'

    }

}
