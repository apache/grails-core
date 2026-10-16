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

package org.grails.gorm.rx.transform

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.tools.GenericsUtils
import rx.Observable
import rx.Single
import spock.lang.Specification

class RxAstUtilsSpec extends Specification {

    def "isObservableOf(ClassNode, ClassNode) returns true for an Observable of a matching subtype"() {
        given:
        ClassNode cls = observableOf(RxAstUtilsChildFixture)

        expect:
        RxAstUtils.isObservableOf(cls, ClassHelper.make(RxAstUtilsParentFixture))
    }

    def "isObservableOf(ClassNode, ClassNode) returns true for a Single of a matching subtype"() {
        given:
        ClassNode cls = singleOf(RxAstUtilsChildFixture)

        expect:
        RxAstUtils.isObservableOf(cls, ClassHelper.make(RxAstUtilsParentFixture))
    }

    def "isObservableOf(ClassNode, ClassNode) returns false when the generic type does not match the parent"() {
        given:
        ClassNode cls = observableOf(RxAstUtilsUnrelatedFixture)

        expect:
        !RxAstUtils.isObservableOf(cls, ClassHelper.make(RxAstUtilsParentFixture))
    }

    def "isObservableOf(ClassNode, ClassNode) returns false when the class is neither an Observable nor a Single"() {
        given:
        ClassNode cls = ClassHelper.make(RxAstUtilsChildFixture)

        expect:
        !RxAstUtils.isObservableOf(cls, ClassHelper.make(RxAstUtilsParentFixture))
    }

    def "isObservableOf(ClassNode, ClassNode) returns false when there are no generic types"() {
        given:
        ClassNode cls = ClassHelper.make(Observable).plainNodeReference

        expect:
        !RxAstUtils.isObservableOf(cls, ClassHelper.make(RxAstUtilsParentFixture))
    }

    def "isObservableOf(ClassNode, Class) returns true for an Observable of a matching subtype"() {
        given:
        ClassNode cls = observableOf(RxAstUtilsChildFixture)

        expect:
        RxAstUtils.isObservableOf(cls, RxAstUtilsParentFixture)
    }

    def "isObservableOf(ClassNode, Class) returns false for a Single since only Observable is checked"() {
        given:
        ClassNode cls = singleOf(RxAstUtilsChildFixture)

        expect:
        !RxAstUtils.isObservableOf(cls, RxAstUtilsParentFixture)
    }

    def "isObservableOf(ClassNode, Class) returns false when the generic type does not match the parent"() {
        given:
        ClassNode cls = observableOf(RxAstUtilsUnrelatedFixture)

        expect:
        !RxAstUtils.isObservableOf(cls, RxAstUtilsParentFixture)
    }

    private static ClassNode observableOf(Class genericType) {
        GenericsUtils.makeClassSafeWithGenerics(Observable, ClassHelper.make(genericType))
    }

    private static ClassNode singleOf(Class genericType) {
        GenericsUtils.makeClassSafeWithGenerics(Single, ClassHelper.make(genericType))
    }
}

interface RxAstUtilsParentFixture {
}

class RxAstUtilsChildFixture implements RxAstUtilsParentFixture {
}

class RxAstUtilsUnrelatedFixture {
}
