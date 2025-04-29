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

package demo

import grails.testing.mixin.integration.Integration
import groovy.json.JsonOutput
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
@Integration
@IgnoreIf({ os.windows })
class AuthorIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating an author"() {
        when:
        String curlCommand = '''
            // tag::createCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
mutation {
  authorCreate(author: {
    name: "Sally",
    homeLocation: {
      lat: "41.101539",
      long: "-80.653381"
    },
    books: [
      {key: "0307887448", value: {title: "Ready Player One"}},
      {key: "0743264746", value: {title: "Einstein: His Life and Universe"}}
    ]
  }) {
    id
    name
    homeLocation {
      lat
      long
    }
    books {
      key
      value {
        id
        title
      }
    }  
    errors {
      field
      message
    }
  }
}'
            // end::createCommand[]
        '''.toString().replace('{url}', getUrl())

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::createResponse[]
{
    "data": {
        "authorCreate": {
            "id": 1,
            "name": "Sally",
            "homeLocation": {
                "lat": "41.101539",
                "long": "-80.653381"
            },
            "books": [
                {
                    "key": "0743264746",
                    "value": {
                        "id": 1,
                        "title": "Einstein: His Life and Universe"
                    }
                },
                {
                    "key": "0307887448",
                    "value": {
                        "id": 2,
                        "title": "Ready Player One"
                    }
                }
            ],
            "errors": [
                
            ]
        }
    }
}
// end::createResponse[]
""".replace('\n// tag::createResponse[]\n', '')
   .replace('\n// end::createResponse[]\n', '')

    }
}
