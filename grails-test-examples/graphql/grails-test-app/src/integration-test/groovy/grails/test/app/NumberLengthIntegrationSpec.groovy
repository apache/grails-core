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
import org.grails.web.json.JSONObject
import spock.lang.Shared
import spock.lang.Specification

@Integration
class NumberLengthIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating with numbers valid"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data.numberLengthCreate.id
    }

    void "test creating with numbers too long long"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${BigInteger.valueOf(Long.MAX_VALUE.longValue()) + 1}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }

    void "test creating with numbers too long short"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE + 1},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }

    void "test creating with numbers too long int"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Long.valueOf(Integer.MAX_VALUE) + 1},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }

    void "test creating with numbers too long byte"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE + 1},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }
}
