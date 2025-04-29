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
package grails.doc.internal

import org.junit.jupiter.api.Test

class StringEscapeCategoryTests {

    @Test
    void testEncodeAsUrl() {
        assert StringEscapeCategory.encodeAsUrlPath("test") == "test"
        assert StringEscapeCategory.encodeAsUrlPath("test space") == "test%20space"
        assert StringEscapeCategory.encodeAsUrlPath("multi-byte⁄ space") == "multi-byte%E2%81%84%20space"
        assert StringEscapeCategory.encodeAsUrlPath("test&amp;") == "test&amp;"
        assert StringEscapeCategory.encodeAsUrlPath("<test%20hey>") == "%3Ctest%2520hey%3E"
    }

    @Test
    void testEncodeAsUrlFragment() {
        assert StringEscapeCategory.encodeAsUrlFragment("test") == "test"
        assert StringEscapeCategory.encodeAsUrlFragment("test space") == "test%20space"
        assert StringEscapeCategory.encodeAsUrlFragment("multi-byte⁄ space") == "multi-byte%E2%81%84%20space"
        assert StringEscapeCategory.encodeAsUrlFragment("test&amp;") == "test&amp;"
        assert StringEscapeCategory.encodeAsUrlFragment("<test%20hey>") == "%3Ctest%2520hey%3E"
    }

    @Test
    void testEncodeAsHtml() {
        assert StringEscapeCategory.encodeAsHtml("test") == "test"
        assert StringEscapeCategory.encodeAsHtml("test space") == "test space"
        assert StringEscapeCategory.encodeAsHtml("multi-byte⁄ space") == "multi-byte&frasl; space"
        assert StringEscapeCategory.encodeAsHtml("test&amp;") == "test&amp;amp;"
        assert StringEscapeCategory.encodeAsHtml("<test%20hey>") == "&lt;test%20hey&gt;"
    }
}
