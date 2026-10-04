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

import groovy.transform.PackageScope
import spock.lang.Specification

class ReflectionUtilsSpec extends Specification {

    void "test a method overridden from a public parent method is detected as overridden"() {
        given: "a method overridden from a public superclass"
        def method = ReflectionUtilsChild.getDeclaredMethod('greet')

        expect: "the method is reported as overridden"
        ReflectionUtils.isMethodOverriddenFromParent(method)
    }

    void "test a method unique to the subclass is not detected as overridden"() {
        given: "a method that is only declared on the subclass"
        def method = ReflectionUtilsChild.getDeclaredMethod('onlyOnChild')

        expect: "the method is not reported as overridden"
        !ReflectionUtils.isMethodOverriddenFromParent(method)
    }

    void "test a method whose declaring class has no superclass is not detected as overridden"() {
        given: "a method declared on a class with no superclass"
        def method = Object.getMethod('toString')

        expect: "the method is not reported as overridden"
        !ReflectionUtils.isMethodOverriddenFromParent(method)
    }

    void "test a method that only shadows a non-public parent method is not detected as overridden"() {
        given: "a package-private method also declared on the superclass"
        def method = ReflectionUtilsChild.getDeclaredMethod('hidden')

        expect: "the method is not reported as overridden because the superclass method is not public"
        !ReflectionUtils.isMethodOverriddenFromParent(method)
    }
}

class ReflectionUtilsParent {
    void greet() {
    }

    @PackageScope
    void hidden() {
    }
}

class ReflectionUtilsChild extends ReflectionUtilsParent {
    @Override
    void greet() {
    }

    void onlyOnChild() {
    }

    @PackageScope
    @Override
    void hidden() {
    }
}
