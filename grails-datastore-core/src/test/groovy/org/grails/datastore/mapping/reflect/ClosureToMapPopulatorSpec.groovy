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
package org.grails.datastore.mapping.reflect

import spock.lang.Specification

class ClosureToMapPopulatorSpec extends Specification {

    void "populate captures property assignments and method calls"() {
        when:
        Map result = new ClosureToMapPopulator().populate {
            foo = 'bar'
            one 'two'
            three 'four', 'five'
        }

        then:
        result.foo == 'bar'
        result.one == 'two'
        result.three == ['four', 'five']
        result.size() == 3
    }

    void "populate ignores null property values but records a null single argument"() {
        when:
        Map result = new ClosureToMapPopulator().populate {
            foo = null
            bar null
            baz 'kept'
        }

        then:
        !result.containsKey('foo')
        result.containsKey('bar')
        result.bar == null
        result.baz == 'kept'
    }

    void "invokeMethod ignores a null argument object"() {
        given:
        ClosureToMapPopulator populator = new ClosureToMapPopulator()

        when:
        populator.invokeMethod('foo', null)

        then:
        populator.populate { -> }.isEmpty()
    }

    void "populate writes into the supplied map"() {
        given:
        Map target = [existing: 1]

        when:
        Map result = new ClosureToMapPopulator(target).populate {
            added = 2
        }

        then:
        result.is(target)
        target == [existing: 1, added: 2]
    }

    void "invokeMethod stores a single non-array argument directly"() {
        given:
        ClosureToMapPopulator populator = new ClosureToMapPopulator()

        when:
        Object returned = populator.invokeMethod('key', 'value')

        then:
        returned == null
        populator.populate { -> } == [key: 'value']
    }

    void "the closure resolves against the populator first"() {
        given:
        Map result = [:]

        when:
        new ClosureToMapPopulator(result).populate {
            nested = [a: 1]
            list 1, 2, 3
        }

        then:
        result.nested == [a: 1]
        result.list == [1, 2, 3]
    }
}
