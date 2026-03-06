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

import org.hibernate.query.QueryFlushMode
import spock.lang.Specification
import spock.lang.Unroll

class GrailsHibernateQueryUtilsSpec extends Specification {

    @Unroll
    def "test convertQueryFlushMode for #input"() {
        expect:
        HibernateHqlQuery.convertQueryFlushMode(input) == expected

        where:
        input               | expected
        "ALWAYS"            | QueryFlushMode.FLUSH
        "MANUAL"            | QueryFlushMode.NO_FLUSH
        "COMMIT"            | QueryFlushMode.NO_FLUSH
        "AUTO"              | QueryFlushMode.DEFAULT
        null                | QueryFlushMode.DEFAULT
        "INVALID"           | QueryFlushMode.NO_FLUSH // defaults to COMMIT which is NO_FLUSH
    }
}
