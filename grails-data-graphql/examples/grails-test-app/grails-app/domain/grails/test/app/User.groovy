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

import grails.test.app.pogo.Profile
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class User {

    Integer addedNumbers //addedNumbers is calculated based on additional input properties

    User manager //self referencing toOne

    Profile profile //embedded pogo class
    Address address //embedded domain class

    static constraints = {
        manager nullable: true
    }

    static embedded = ['address', 'profile']

    static graphql = GraphQLMapping.build {
        add('firstNumber', Integer) {
            //don't include this property in the list of properties to return from operations
            output(false)
            nullable(false)
        }
        add('secondNumber', Integer) {
            //don't include this property in the list of properties to return from operations
            output(false)
            nullable(false)
        }
        //don't allow users to specify this property when creating or updating user instances
        property('addedNumbers', input: false)

    }
}
