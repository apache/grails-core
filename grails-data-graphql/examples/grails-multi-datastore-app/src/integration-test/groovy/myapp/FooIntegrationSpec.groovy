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

package myapp

import grails.test.mixin.integration.Integration
import org.grails.datastore.gorm.GormEnhancer
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.orm.hibernate.HibernateDatastore

@Integration
class FooIntegrationSpec implements GraphQLSpec {

    void "test a foo can be created"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                fooCreate(foo: {name: "x"}) {
                    id
                    errors {
                        field
                        message
                    }
                }
            }
        """)
        Map obj = resp.body().data.fooCreate

        then:
        obj.id == 1
        GormEnhancer.findStaticApi(Foo).datastore instanceof HibernateDatastore
    }
}
