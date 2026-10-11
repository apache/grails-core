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
package org.grails.spring.beans.factory

import spock.lang.Specification

class InstanceFactoryBeanSpec extends Specification {

    void 'isSingleton is always true'() {
        expect:
        new InstanceFactoryBean<String>().isSingleton()
    }

    void 'no-arg constructor starts with no object'() {
        when:
        InstanceFactoryBean<String> bean = new InstanceFactoryBean<>()

        then:
        bean.getObject() == null
    }

    void 'single-arg constructor infers the object type from the instance class'() {
        given:
        InstanceFactoryBean<String> bean = new InstanceFactoryBean<>('hello')

        expect:
        bean.getObject() == 'hello'
        bean.getObjectType() == String
    }

    void 'two-arg constructor uses the explicitly supplied object type'() {
        given:
        InstanceFactoryBean<String> bean = new InstanceFactoryBean<>('hello', CharSequence)

        expect:
        bean.getObject() == 'hello'
        bean.getObjectType() == CharSequence
    }

    void 'setObject and setObjectType update the held values'() {
        given:
        InstanceFactoryBean<String> bean = new InstanceFactoryBean<>()

        when:
        bean.setObject('world')
        bean.setObjectType(CharSequence)

        then:
        bean.getObject() == 'world'
        bean.getObjectType() == CharSequence
    }

    void 'getObjectType falls back to the object class when no explicit type was set'() {
        given:
        InstanceFactoryBean<String> bean = new InstanceFactoryBean<>()

        when:
        bean.setObject('world')

        then:
        bean.getObjectType() == String
    }

}
