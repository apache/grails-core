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

package grails.test.app

import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification

@Integration
class GrailsTeamMemberIntegrationSpec extends Specification implements GraphQLSpec {

    void "test retrieving a page of results"() {
        def resp = graphQL.graphql("""
            {           
                grailsTeamMemberList(max: 5, offset: 0, sort: "name") {
                    results {
                        name
                    }
                    totalCount
                }
            }
        """)
        Map data = resp.body().data.grailsTeamMemberList
        JSONArray results = data.results

        expect:
        data.totalCount == 16
        results.size() == 5
        results[0].name == 'Alvaro'
        results[1].name == 'Ben'
        results[2].name == 'Colin'
        results[3].name == 'Dave'
        results[4].name == 'Graeme'
    }

    void "test retrieving the next page of results"() {
        def resp = graphQL.graphql("""
            {           
                grailsTeamMemberList(max: 5, offset: 5, sort: "name") {
                    results {
                        name
                    }
                    totalCount
                }
            }
        """)
        Map data = resp.body().data.grailsTeamMemberList
        JSONArray results = data.results

        expect:
        data.totalCount == 16
        results.size() == 5
        results[0].name == 'Ivan'
        results[1].name == 'Jack'
        results[2].name == 'James'
        results[3].name == 'Jeff'
        results[4].name == 'Matthew'
    }

}
