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

import spock.lang.Specification

import org.codehaus.groovy.runtime.NullObject
import org.grails.web.converters.ConverterUtil;

class ConverterUtilSpec extends Specification {

    void 'Test converting an array to an interface type'() {
        given:
            def converterUtil = new ConverterUtil()

        when:
            def someArray = ['One', 'Two', 'One', 'Three'] as String[]
            def someSet = converterUtil.invokeOriginalAsTypeMethod(someArray, Set)

        then:
            someSet instanceof Set
    }

    void 'Test converting an NullObject to type'() {
        given:
        def converterUtil = new ConverterUtil()

        when:

            def result = converterUtil.invokeOriginalAsTypeMethod(NullObject.getNullObject(), Long)

        then:
            result == null
    }
}
