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
package org.grails.web.converters

import static org.junit.Assert.assertEquals

import org.grails.web.json.JSONArray
import org.junit.Test

class JSONArrayTests {

    @Test
    void testEquals() {
        assertEquals(getJSONArray(), getJSONArray())
    }

    @Test
    void testHashCode() {
        assertEquals(getJSONArray().hashCode(), getJSONArray().hashCode())
    }

    private getJSONArray() { new JSONArray(['a', 'b', 'c']) }
}
