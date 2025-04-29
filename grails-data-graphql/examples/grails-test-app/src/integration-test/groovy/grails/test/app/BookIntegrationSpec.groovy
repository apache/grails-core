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

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class BookIntegrationSpec extends Specification implements GraphQLSpec {

    void "test books cannot be queried directly"() {
        when:
        def resp = graphQL.graphql("""
            {
              bookList {
                id
              }
            }
        """)

        def result = resp.body()

        then:
        result.errors.size() == 1
        result.errors[0].message == "Validation error (FieldUndefined@[bookList]) : Field 'bookList' in type 'Query' is undefined"
    }

}
