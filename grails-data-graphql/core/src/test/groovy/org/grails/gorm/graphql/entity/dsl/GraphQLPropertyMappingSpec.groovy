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

package org.grails.gorm.graphql.entity.dsl

import spock.lang.Specification

class GraphQLPropertyMappingSpec extends Specification {

    void "test build"() {
        given:
        GraphQLPropertyMapping mapping = GraphQLPropertyMapping.build {
            input false
            output false
            deprecated true
            deprecationReason 'reason'
            description 'description'
            dataFetcher {

            }
        }

        expect:
        !mapping.input
        !mapping.output
        mapping.deprecated
        mapping.deprecationReason == 'reason'
        mapping.description == 'description'
        mapping.dataFetcher instanceof Closure
    }
}
