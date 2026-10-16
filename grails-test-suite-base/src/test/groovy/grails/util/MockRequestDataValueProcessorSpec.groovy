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
package grails.util

import jakarta.servlet.http.HttpServletRequest

import spock.lang.Specification

class MockRequestDataValueProcessorSpec extends Specification {

    MockRequestDataValueProcessor processor = new MockRequestDataValueProcessor()
    HttpServletRequest request = Stub(HttpServletRequest)

    void 'extra hidden fields are always the same marker pair'() {
        expect:
        processor.getExtraHiddenFields(request) == [requestDataValueProcessorHiddenName: 'hiddenValue']
    }

    void 'form field values are marked as processed'() {
        expect:
        processor.processFormFieldValue(request, 'name', 'value', 'text') == 'value_PROCESSED_'
    }

    void 'processUrl appends a marker query parameter, respecting an existing query or fragment'() {
        expect:
        processor.processUrl(request, '/book/list') == '/book/list?requestDataValueProcessorParamName=paramValue'
        processor.processUrl(request, '/book/list?sort=asc') == '/book/list?sort=asc&requestDataValueProcessorParamName=paramValue'
        processor.processUrl(request, '/book/list#top') == '/book/list?requestDataValueProcessorParamName=paramValue#top'
    }

    void 'processAction strips the marker parameter added by processUrl in every position'() {
        expect:
        processor.processAction(request, '/book/save?requestDataValueProcessorParamName=paramValue&id=1', 'POST') == '/book/save?id=1'
        processor.processAction(request, '/book/save?id=1&requestDataValueProcessorParamName=paramValue', 'POST') == '/book/save?id=1'
        processor.processAction(request, '/book/save?requestDataValueProcessorParamName=paramValue', 'POST') == '/book/save'
        processor.processAction(request, '/book/save', 'POST') == '/book/save'
    }

}
