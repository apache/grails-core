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
package org.grails.orm.hibernate.query

import org.grails.datastore.mapping.query.Query
import spock.lang.Specification
import spock.lang.Unroll

class ProjectionPredicateSpec extends Specification {

    def "test projection predicate"() {
        given:
        def predicate = new ProjectionPredicate()

        expect:
        predicate.test(projection) == expected

        where:
        projection                       | expected
        new Query.IdProjection()         | true
        new Query.PropertyProjection("a")| true
        new Query.CountProjection()      | true
        new Query.CountDistinctProjection("a") | true
        new Query.MaxProjection("a")     | true
        new Query.MinProjection("a")     | true
        new Query.SumProjection("a")     | true
        new Query.AvgProjection("a")     | true
        new Query.DistinctProjection()   | true
        new Query.GroupPropertyProjection("a") | true
    }
}
