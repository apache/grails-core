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
package org.grails.web.json.parser

import groovy.transform.CompileStatic
import org.grails.web.json.JSONObject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@CompileStatic
class JSONParserSpec extends Specification {

    void "Test JSONParser.parseJSON() for long array"() {
        given: "JSONParser with input stream containing large array data"
        def largeArray = generateByteArray(15000)
        def inputStream = getJsonObjectInputStream(largeArray)
        JSONParser jsonParser = new JSONParser(inputStream)
        def expectedArray = largeArray

        when: "parsing object with long array"
        JSONObject jsonElement = jsonParser.parseJSON() as JSONObject

        then: "data is parsed as expected"
        jsonElement
        byte[] actualArray = jsonElement.get('array') as byte[]
        expectedArray == actualArray
    }

    private static InputStream getJsonObjectInputStream(byte[] array) {
        String arrayObjectStr = "{\"array\": ${array}}"
        return new ByteArrayInputStream(arrayObjectStr.getBytes(StandardCharsets.UTF_8))
    }

    private static byte[] generateByteArray(int length) {
        byte[] byteArray = new byte[length]
        new Random().nextBytes(byteArray)
        byteArray
    }
}
