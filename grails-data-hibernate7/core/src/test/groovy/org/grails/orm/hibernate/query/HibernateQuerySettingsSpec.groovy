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

class HibernateQuerySettingsSpec extends Specification {

    void "no arguments leave every setting unset"() {
        expect:
        HibernateQuerySettings.from(args).is(HibernateQuerySettings.NONE)

        where:
        args << [null, [:]]
    }

    void "arguments that are not query settings leave every setting unset"() {
        when:
        HibernateQuerySettings settings = HibernateQuerySettings.from([max: 5, offset: 1, sort: 'name', cache: true])

        then:
        settings == HibernateQuerySettings.NONE
    }

    @Unroll
    void "#key: #raw is read as #expected"() {
        when:
        HibernateQuerySettings settings = HibernateQuerySettings.from([(key): raw])

        then:
        settings."$property" == expected

        where:
        key         | raw        | property    | expected
        'fetchSize' | 50         | 'fetchSize' | 50
        'fetchSize' | '50'       | 'fetchSize' | 50
        'fetchSize' | 0          | 'fetchSize' | 0
        'timeout'   | 7L         | 'timeout'   | 7
        'timeout'   | '7'        | 'timeout'   | 7
        'readOnly'  | true       | 'readOnly'  | true
        'readOnly'  | false      | 'readOnly'  | false
        'readOnly'  | 'true'     | 'readOnly'  | true
        'readOnly'  | 'false'    | 'readOnly'  | false
        'flushMode' | 'MANUAL'   | 'flushMode' | QueryFlushMode.NO_FLUSH
        'flushMode' | 'ALWAYS'   | 'flushMode' | QueryFlushMode.FLUSH
        'flushMode' | 'AUTO'     | 'flushMode' | QueryFlushMode.DEFAULT
    }

    void "settings are independent of each other"() {
        when:
        HibernateQuerySettings settings = HibernateQuerySettings.from([timeout: 3])

        then:
        settings.timeout() == 3
        settings.fetchSize() == null
        settings.readOnly() == null
        settings.flushMode() == null
    }

    @Unroll
    void "#key: '#raw' is rejected by naming the argument and the value"() {
        when:
        HibernateQuerySettings.from([(key): raw])

        then:
        IllegalArgumentException e = thrown()
        e.message == "Query argument '${key}' must be a whole number but was '${raw}'".toString()

        where:
        key         | raw
        'fetchSize' | 'lots'
        'timeout'   | '1.5s'
    }
}
