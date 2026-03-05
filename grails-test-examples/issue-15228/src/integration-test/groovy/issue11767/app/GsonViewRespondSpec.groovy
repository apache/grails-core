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
package issue11767.app

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class GsonViewRespondSpec extends Specification implements HttpClientSupport {

    void 'respond with Error gson view'() {
        when: 'The app controller is visited on errorView'
        def response = http('/app/errorView?foo=Too+Short', Accept: 'application/json')

        then:
        response.expectJson(422, '''
            {
              "error": {
                "errors": [
                  {
                    "field": "foo",
                    "rejectedValue": "Too Short",
                    "message": "Property [foo] of class [class issue15228.app.ValidateableObject] with value [Too Short] is less than the minimum size of [10]"
                  }
                ]
              }
            }
        ''')
    }

    void 'respond with gson view from action name'() {
        when: 'The app controller is visited on normalView'
        def response = http('/app/normalView?foo=Testing+normal+view', Accept: 'application/json')

        then:
        response.expectJson('''{
              "normal": {
                "foo": "Testing normal view"
              } 
           }
       ''')
    }

    void 'respond with gson view from type'() {
        when: 'The app controller is visited on typeView'
        def response = http('/app/typeView?foo=Testing+type+view', Accept: 'application/json')

        then:
        response.expectJson('''{
              "type": {
                "foo": "Testing type view"
              } 
           }
       ''')
    }
}
