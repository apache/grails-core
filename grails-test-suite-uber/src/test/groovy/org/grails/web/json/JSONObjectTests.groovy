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
package org.grails.web.json

import org.junit.jupiter.api.Test
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JSONObjectTests {

    @Test
    void testContainsKey() {
        JSONObject j = new JSONObject()
        j.put('test', 1)
        assert j.containsKey('test')
    }

    void testContainsValue() {
        JSONObject j = new JSONObject()
        j.put('test', 1)
        assert j.containsValue(1)
    }

    @Issue("GRAILS-10412")
    void testToStringWithArray() {
        JSONObject j = new JSONObject([id:1, tags:['tag13333', 'tag2231']])

        JSONObject json = grails.converters.JSON.parse(j.toString())
        assertEquals(json.id, 1)
        assertEquals(json.tags[0], "tag13333")
        assertEquals(json.tags[1], "tag2231")
    }

    void testEqualityOfJSONObjectsReturnedFromConverter() {
        // GRAILS-7417

        def input = '''{"message":"mockMessage","errors":[{"field":"name","errorMessage":"mockMessage","errorType":"validation","fieldValue":null}],
        "object":{"nest2":{"p2":"val2","p1":"val1"},"nest1":{"p2":"val2","p1":"val1"},"name":null},"success":false}'''

        JSONObject j1 = grails.converters.JSON.parse(input)
        JSONObject j2 = grails.converters.JSON.parse(input)

        assertNotSame j1, j2
        assertEquals j1, j2
        assertTrue j1 == j2
    }

    void testJSONObjectShouldSupportBigIntegerAndBigDecimal() {
        String input = '''
            {"v1":0, "v2":9999999999999999, "v3":88888888888888888888888888888888,
            "v4":-11111, "v5":1.1111111E15, "v6":-8.88888888888888888E25,
            "v7":0.00, "v8":999.999, "v9":333.333333333333, "v10":1.1111E-7,
            "v11":-9.33333333333E-20}
        '''

        JSONObject json = grails.converters.JSON.parse(input)

        assertEquals(json.v1, Integer.valueOf(0))
        assertEquals(json.v2, Long.valueOf(9999999999999999L))
        assertEquals(json.v3, new BigInteger('88888888888888888888888888888888'))
        assertEquals(json.v4, Integer.valueOf(-11111))
        assertEquals(json.v5, Long.valueOf(1111111100000000L))
        assertEquals(json.v6, new BigInteger('-88888888888888888800000000'))
        assertEquals(json.v7, Double.valueOf(0.0))
        assertEquals(json.v8, Double.valueOf(999.999))
        assertEquals(json.v9, new BigDecimal('333.333333333333'))
        assertEquals(json.v10, Double.valueOf(1.1111E-7))
        assertEquals(json.v11, new BigDecimal('-9.33333333333E-20'))
    }

}
