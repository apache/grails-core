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
package org.grails.orm.hibernate.support

import spock.lang.Specification

class SoftKeySpec extends Specification {

    static class TestSoftKey<T> extends SoftKey<T> {
        boolean forceNull = false
        TestSoftKey(T referent) {
            super(referent)
        }
        @Override
        T get() {
            return forceNull ? null : super.get()
        }
    }

    def "constructor stores referent and computes hashCode from it"() {
        given:
        def key = "hello"

        when:
        def sk = new SoftKey<>(key)

        then:
        sk.get() == key
        sk.hashCode() == key.hashCode()
    }

    def "hashCode is stable even after gc (uses stored hash)"() {
        given:
        def sk = new SoftKey<>("world")

        expect:
        sk.hashCode() == "world".hashCode()
    }

    def "equals returns true for same instance"() {
        given:
        def sk = new SoftKey<>("a")

        expect:
        sk.equals(sk)
    }

    def "equals returns false for null"() {
        given:
        def sk = new SoftKey<>("a")

        expect:
        !sk.equals(null)
    }

    def "equals returns false for different class"() {
        given:
        def sk = new SoftKey<>("a")

        expect:
        !sk.equals("a")
    }

    def "two SoftKeys with equal referents are equal"() {
        given:
        def sk1 = new SoftKey<>("same")
        def sk2 = new SoftKey<>("same")

        expect:
        sk1 == sk2
        sk1.hashCode() == sk2.hashCode()
    }

    def "two SoftKeys with different referents are not equal"() {
        given:
        def sk1 = new SoftKey<>("foo")
        def sk2 = new SoftKey<>("bar")

        expect:
        sk1 != sk2
    }

    def "two SoftKeys with different hashes are not equal"() {
        given:
        // ensure different hash codes (different objects)
        def sk1 = new SoftKey<>(Integer.valueOf(1))
        def sk2 = new SoftKey<>(Integer.valueOf(99999))

        expect:
        sk1 != sk2
    }

    def "equals handles null referent after gc"() {
        given:
        def sk1 = new TestSoftKey<>("a")
        def sk2 = new TestSoftKey<>("a")
        
        when:
        sk1.forceNull = true

        then:
        !sk1.equals(sk2)
        
        when:
        sk2.forceNull = true
        
        then:
        sk1.equals(sk2)
    }

    def "equals returns false if one referent is null and the other is not"() {
        given:
        def sk1 = new TestSoftKey<>("a")
        def sk2 = new TestSoftKey<>("a")

        when:
        sk2.forceNull = true

        then:
        !sk1.equals(sk2)
    }
}
