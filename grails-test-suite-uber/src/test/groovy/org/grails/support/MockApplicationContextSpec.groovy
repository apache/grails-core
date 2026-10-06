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
package org.grails.support

import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.core.ParameterizedTypeReference
import spock.lang.Specification

class MockApplicationContextSpec extends Specification {

    void 'getBean with a ParameterizedTypeReference returns the named bean'() {
        given:
        def context = new MockApplicationContext()
        def names = ['one', 'two']
        context.registerMockBean('names', names)

        when:
        List<String> bean = context.getBean('names', new ParameterizedTypeReference<List<String>>() {})

        then:
        bean.is(names)
    }

    void 'getBean with a ParameterizedTypeReference rejects a bean of another type'() {
        given:
        def context = new MockApplicationContext()
        context.registerMockBean('names', 'not a list')

        when:
        context.getBean('names', new ParameterizedTypeReference<List<String>>() {})

        then:
        thrown(NoSuchBeanDefinitionException)
    }

    void 'getBean with a ParameterizedTypeReference rejects an unknown bean name'() {
        given:
        def context = new MockApplicationContext()

        when:
        context.getBean('missing', new ParameterizedTypeReference<List<String>>() {})

        then:
        thrown(NoSuchBeanDefinitionException)
    }
}
