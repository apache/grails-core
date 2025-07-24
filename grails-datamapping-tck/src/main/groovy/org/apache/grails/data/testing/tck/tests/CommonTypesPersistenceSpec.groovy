/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.CommonTypes

/**
 * @author graemerocher
 */
class CommonTypesPersistenceSpec extends GrailsDataTckSpec {

    def testPersistBasicTypes() {
        given:
        def now = new Date()
        def cal = new GregorianCalendar()
        def ct = new CommonTypes(
                l: 10L,
                b: 10 as byte,
                s: 10 as short,
                bool: true,
                i: 10,
                url: new URL("http://google.com"),
                date: now,
                c: cal,
                bd: 1.0,
                bi: 10 as BigInteger,
                d: 1.0 as Double,
                f: 1.0 as Float,
                tz: TimeZone.getTimeZone("GMT"),
                loc: Locale.UK,
                cur: Currency.getInstance("USD"),
                ba: 'hello'.bytes
        )

        when:
        ct.save(flush: true)
        ct.discard()
        ct = CommonTypes.get(ct.id)

        then:
        ct
        10L == ct.l
        (10 as byte) == ct.b
        (10 as short) == ct.s
        true == ct.bool
        10 == ct.i
        new URL("http://google.com") == ct.url
        now.time == ct.date.time
        cal == ct.c
        1.0 == ct.bd
        10 as BigInteger == ct.bi
        (1.0 as Double) == ct.d
        (1.0 as Float) == ct.f
        TimeZone.getTimeZone("GMT") == ct.tz
        Locale.UK == ct.loc
        Currency.getInstance("USD") == ct.cur
        'hello'.bytes == ct.ba
    }
}
