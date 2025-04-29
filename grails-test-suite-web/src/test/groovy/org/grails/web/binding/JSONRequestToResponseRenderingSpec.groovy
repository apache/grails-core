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
package org.grails.web.binding

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class JSONRequestToResponseRenderingSpec extends Specification implements ControllerUnitTest<ArrayController> {

    def "Test that JSON arrays are correctly converted in controllers"() {
        given:"A JSON request containing arrays"
            request.json = '''
            {
	"track": {
		"start_time": 1316975696560,
		"segments": [
			{
				"coordinates": [
					[
						47.8897441833333,
						-122.732959033333,
						101.1,
						1316975697100
					],
					[
						47.8898427833333,
						-122.732921583333,
						109.4,
						1316975704100
					]
				]
			}
		]
	}
}
'''
        when:"The params are rendered as JSON"
            controller.list()
        then:"Check that the JSON is convereted back correctly"
            response.json.track.segments != null
    }
}

@Artefact('Controller')
class ArrayController {
    def list() {
        def json = request.JSON
        render json
    }
}
