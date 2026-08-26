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
package org.grails.web.taglib

import spock.lang.Specification

import org.grails.plugins.web.taglib.ApplicationTagLib
import grails.testing.web.taglib.TagLibUnitTest

/**
 * The set tag keeps writing into the scope when it is given a type; what the type adds is that the
 * page declares the variable rather than looking it up.
 */
class TypedSetTagSpec extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    void 'a typed set writes the same value into the scope it always did'() {
        expect:
        applyTemplate('<g:set type="int" var="n" value="${2}"/>${n}|${pageScope.n}') == '2|2'
    }

    void 'the scope a typed set writes to is still the one it was told'() {
        expect:
        applyTemplate('<g:set type="int" var="n" scope="request" value="${2}"/>${n}|${request.n}') == '2|2'
    }

    void 'an untyped set is unchanged'() {
        expect:
        applyTemplate('<g:set var="n" value="${2}"/>${n}|${pageScope.n}') == '2|2'
    }
}
