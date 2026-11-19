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
package org.grails.datastore.mapping.internal

import spock.lang.Specification
import spock.lang.Unroll

class ClassKeyedSpec extends Specification {

    @Unroll
    void 'the constant for #instance.class.simpleName is #expected'() {
        expect:
        ClassKeyed.forInstance(Shape, instance) == expected

        where:
        instance            | expected
        new ArrayList()     | Shape.LIST
        new LinkedList()    | Shape.LIST
        new HashSet()       | Shape.EVERYTHING_ELSE
        'text'              | Shape.EVERYTHING_ELSE
    }

    void 'a constant for a class is chosen over the default whatever the order, and the default only when none is listed'() {
        expect:
        ClassKeyed.forInstance(Ordered, new ArrayList()) == Ordered.LIST
        ClassKeyed.forInstance(Ordered, 'text') == Ordered.ANY
    }

    void 'null is told apart as the default'() {
        expect:
        ClassKeyed.forInstance(Ordered, null) == Ordered.ANY
    }

    void 'a constant listing no class is never chosen, and an enum without a default rejects an unlisted object'() {
        when:
        ClassKeyed.forInstance(Partial, new HashSet())

        then:
        IllegalArgumentException e = thrown()
        e.message == "${Partial.name} has no constant for ${HashSet.name}"

        and:
        ClassKeyed.forInstance(Partial, new ArrayList()) == Partial.LIST
    }

    void 'an enum without a default rejects null with a message that names it'() {
        when:
        ClassKeyed.forInstance(Partial, null)

        then:
        IllegalArgumentException e = thrown()
        e.message == "${Partial.name} has no constant for null"
    }

    void 'a constant for a subclass is chosen when it is declared before its superclass, and not otherwise'() {
        expect:
        ClassKeyed.forInstance(SubclassFirst, new ArrayList()) == SubclassFirst.ARRAY_LIST
        ClassKeyed.forInstance(SubclassFirst, new LinkedList()) == SubclassFirst.LIST
        ClassKeyed.forInstance(SuperclassFirst, new ArrayList()) == SuperclassFirst.LIST
    }

    enum Shape implements ClassKeyed<Shape> {
        LIST(ArrayList, LinkedList),
        EVERYTHING_ELSE(Object)

        private final Class<?>[] keyClasses

        Shape(Class<?>... keyClasses) {
            this.keyClasses = keyClasses
        }

        @Override
        Class<?>[] keyClasses() {
            keyClasses
        }
    }

    enum Ordered implements ClassKeyed<Ordered> {
        ANY(Object),
        LIST(ArrayList)

        private final Class<?>[] keyClasses

        Ordered(Class<?>... keyClasses) {
            this.keyClasses = keyClasses
        }

        @Override
        Class<?>[] keyClasses() {
            keyClasses
        }
    }

    enum Partial implements ClassKeyed<Partial> {
        NOT_A_KEY(),
        LIST(ArrayList)

        private final Class<?>[] keyClasses

        Partial(Class<?>... keyClasses) {
            this.keyClasses = keyClasses
        }

        @Override
        Class<?>[] keyClasses() {
            keyClasses
        }
    }

    enum SubclassFirst implements ClassKeyed<SubclassFirst> {
        ARRAY_LIST(ArrayList),
        LIST(List)

        private final Class<?>[] keyClasses

        SubclassFirst(Class<?>... keyClasses) {
            this.keyClasses = keyClasses
        }

        @Override
        Class<?>[] keyClasses() {
            keyClasses
        }
    }

    enum SuperclassFirst implements ClassKeyed<SuperclassFirst> {
        LIST(List),
        ARRAY_LIST(ArrayList)

        private final Class<?>[] keyClasses

        SuperclassFirst(Class<?>... keyClasses) {
            this.keyClasses = keyClasses
        }

        @Override
        Class<?>[] keyClasses() {
            keyClasses
        }
    }
}
