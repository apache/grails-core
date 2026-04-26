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

package grails.tenant.app

import grails.testing.mixin.integration.Integration
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class UserIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating a user without a company"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '1')

        when:
        def resp = graphQL.graphql("""
            mutation {
                userCreate(user: {
                    name: "Sally"
                }) {
                    id
                    name
                    companyId
                }
            }
        """)
        Map obj = resp.body().data.userCreate

        then: "The company is supplied via multi-tenancy"
        obj.id == 1
        obj.name == "Sally"
        obj.companyId == '1'
    }

    void "test creating other users with a different company"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '2')

        when:
        def resp = graphQL.graphql("""
            mutation {
                john: userCreate(user: {
                    name: "John"
                }) {
                    id
                    name
                    companyId
                }
                
                joe: userCreate(user: {
                    name: "Joe"
                }) {
                    id
                    name
                    companyId
                }
            }
        """)
        Map obj = resp.body().data

        then: "The company is supplied via multi-tenancy"
        obj.john.name == 'John'
        obj.john.companyId == '2'
        obj.joe.name == 'Joe'
        obj.joe.companyId == '2'
    }

    void "test retrieving a list of users in company 1"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '1')

        when:
        def resp = graphQL.graphql("""
            {
                userList {
                    name
                }
            }
        """)
        List obj = resp.body().data.userList

        then: "The list is filtered by the company"
        obj.size() == 1
        obj[0].name == 'Sally'
    }

    void "test retrieving a list of users in company 2"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '2')

        when:
        def resp = graphQL.graphql("""
            {
                userList {
                    name
                }
            }
        """)
        List obj = resp.body().data.userList

        then: "The list is filtered by the company"
        obj.size() == 2
        obj.find { it.name == 'Joe' }
        obj.find { it.name == 'John' }
    }

}
