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
package org.grails.datastore.gorm.bootstrap.support

import spock.lang.Specification

class InstanceFactoryBeanSpec extends Specification {

    void "test no-arg constructor leaves object and objectType unset"() {
        given:
        def bean = new InstanceFactoryBean<String>()

        expect:
        bean.object == null
        bean.getObject() == null
    }

    void "test single-arg constructor derives the object type from the object's class"() {
        given:
        def bean = new InstanceFactoryBean<String>('hello')

        expect:
        bean.getObject() == 'hello'
        bean.getObjectType() == String
    }

    void "test two-arg constructor uses the explicitly given object type"() {
        given:
        def bean = new InstanceFactoryBean<String>('hello', CharSequence)

        expect:
        bean.getObject() == 'hello'
        bean.getObjectType() == CharSequence
    }

    void "test getObjectType falls back to the object's class when no explicit type was set"() {
        given:
        def bean = new InstanceFactoryBean<String>()
        bean.setObject('hello')

        expect:
        bean.getObjectType() == String
    }

    void "test setObjectType overrides the type returned by getObjectType"() {
        given:
        def bean = new InstanceFactoryBean<String>('hello')

        when:
        bean.setObjectType(CharSequence)

        then:
        bean.getObjectType() == CharSequence
    }

    void "test isSingleton always returns true"() {
        expect:
        new InstanceFactoryBean<String>().isSingleton()
    }
}
