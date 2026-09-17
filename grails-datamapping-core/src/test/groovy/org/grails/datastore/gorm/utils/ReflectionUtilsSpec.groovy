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
package org.grails.datastore.gorm.utils

import spock.lang.Specification

class ReflectionUtilsSpec extends Specification {

    void "test isMethodOverriddenFromParent returns true when the superclass declares the same method"() {
        given:
        def method = Child.getMethod('greet')

        expect:
        ReflectionUtils.isMethodOverriddenFromParent(method)
    }

    void "test isMethodOverriddenFromParent returns false when the superclass does not declare the method"() {
        given:
        def method = Child.getMethod('onlyOnChild')

        expect:
        !ReflectionUtils.isMethodOverriddenFromParent(method)
    }

    void "test isMethodOverriddenFromParent returns false when there is no superclass"() {
        given:
        def method = Root.getMethod('rootOnly')

        expect:
        !ReflectionUtils.isMethodOverriddenFromParent(method)
    }

    static class Parent {
        String greet() { 'parent' }
    }

    static class Child extends Parent {
        @Override
        String greet() { 'child' }

        String onlyOnChild() { 'child only' }
    }

    static class Root {
        String rootOnly() { 'root' }
    }
}
