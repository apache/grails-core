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
package grails.util

import spock.lang.Specification

class ClosureToMapPopulatorSpec extends Specification {

    void 'a property assignment inside the closure populates the map'() {
        given:
        ClosureToMapPopulator populator = new ClosureToMapPopulator()

        when:
        Map result = populator.populate {
            name = 'Bob'
            age = 42
        }

        then:
        result == [name: 'Bob', age: 42]
    }

    void 'a single-argument method call populates the map with that argument'() {
        given:
        ClosureToMapPopulator populator = new ClosureToMapPopulator()

        when:
        Map result = populator.populate {
            name('Bob')
        }

        then:
        result == [name: 'Bob']
    }

    void 'a multi-argument method call populates the map with the argument list'() {
        given:
        ClosureToMapPopulator populator = new ClosureToMapPopulator()

        when:
        Map result = populator.populate {
            names('Bob', 'Judy')
        }

        then:
        result == [names: ['Bob', 'Judy']]
    }

    void 'a null property assignment is ignored, but a null method-call argument is stored as a null entry'() {
        given:
        ClosureToMapPopulator populator = new ClosureToMapPopulator()

        when:
        Map result = populator.populate {
            name = null
            age(null)
        }

        then:
        !result.containsKey('name')
        result.containsKey('age')
        result.age == null
    }

    void 'populate starts from and mutates the map supplied to the constructor'() {
        given:
        Map seed = [existing: 'value']
        ClosureToMapPopulator populator = new ClosureToMapPopulator(seed)

        when:
        Map result = populator.populate {
            name = 'Bob'
        }

        then:
        result.is(seed)
        result == [existing: 'value', name: 'Bob']
    }
}
