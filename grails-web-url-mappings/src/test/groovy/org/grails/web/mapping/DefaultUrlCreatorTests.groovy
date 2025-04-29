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
package org.grails.web.mapping

import grails.util.GrailsWebMockUtil
import org.grails.web.mapping.DefaultUrlCreator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.context.request.RequestContextHolder

import static org.junit.jupiter.api.Assertions.assertEquals

class DefaultUrlCreatorTests {

    @Test
    void testCreateUrl() {

        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.currentRequest.characterEncoding = "utf-8"

        def creator = new DefaultUrlCreator("foo", "index")

        assertEquals "/foo/index", creator.createURL(null, "utf-8")
        assertEquals "/foo/index/1", creator.createURL(id:1, "utf-8")
        assertEquals "/foo/index/1?hello=world", creator.createURL(id:1, hello:"world", "utf-8")
        assertEquals "/foo/index/hello+world", creator.createURL(id:"hello world", "utf-8")
        assertEquals "/foo/index?hello=world", creator.createURL(hello:"world", "utf-8")
        assertEquals "/foo/index?hello=world&fred=flintstone", creator.createURL(hello:"world", fred:"flintstone", "utf-8")
    }

    @Test
    void testCreateUrlNoCharacterEncoding() {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.currentRequest.characterEncoding = null

        def creator = new DefaultUrlCreator("foo", "index")

        assertEquals "/foo/index", creator.createURL(null, "utf-8")
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }
}
