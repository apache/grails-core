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
package org.grails.datastore.mapping.engine.event

import java.util.function.BiPredicate

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.types.Association

class ValidationEventSpec extends Specification {

    void 'an event without cascade listeners has no cascade listener'() {
        expect:
        newEvent().cascadeListener == null
    }

    void 'the cascade listener calls every registered listener and says whether any of them changed the object'() {
        given:
        ValidationEvent event = newEvent()
        List<String> called = []
        event.addCascadeListener({ Object object, Association reached -> called << 'first ' + object; firstChanges } as BiPredicate<Object, Association>)
        event.addCascadeListener({ Object object, Association reached -> called << 'second ' + object; secondChanges } as BiPredicate<Object, Association>)

        when:
        boolean changed = event.cascadeListener.test('pet', null)

        then: 'the second listener is called also when the first has changed the object'
        called == ['first pet', 'second pet']
        changed == expected

        where:
        firstChanges | secondChanges || expected
        true         | false         || true
        false        | true          || true
        false        | false         || false
    }

    void 'a cascade listener registered after the cascade listener was obtained is called too'() {
        given:
        ValidationEvent event = newEvent()
        List<String> called = []
        event.addCascadeListener({ Object object, Association reached -> called << 'first'; false } as BiPredicate<Object, Association>)
        BiPredicate<Object, Association> cascadeListener = event.cascadeListener

        when:
        event.addCascadeListener({ Object object, Association reached -> called << 'second'; true } as BiPredicate<Object, Association>)
        boolean changed = cascadeListener.test('pet', null)

        then:
        called == ['first', 'second']
        changed
    }

    private ValidationEvent newEvent() {
        new ValidationEvent(Stub(Datastore), null, null)
    }
}
