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
package org.grails.datastore.mapping.mongo.engine

import spock.lang.Specification

/**
 * The registry compares instances by identity, holds none of them strongly, and ignores null.
 */
class PersistedInstancesSpec extends Specification {

    static class Named {
        String name

        @Override
        boolean equals(Object other) {
            other instanceof Named && ((Named) other).name == name
        }

        @Override
        int hashCode() {
            name.hashCode()
        }
    }

    void "an instance that was added is contained"() {
        given:
        PersistedInstances persisted = new PersistedInstances()
        Named amazon = new Named(name: 'Amazon')

        when:
        persisted.add(amazon)

        then:
        persisted.contains(amazon)
    }

    void "an instance that was not added is not contained"() {
        expect:
        !new PersistedInstances().contains(new Named(name: 'Amazon'))
    }

    void "an instance that equals one that was added is not contained"() {
        given:
        PersistedInstances persisted = new PersistedInstances()
        Named loaded = new Named(name: 'Amazon')
        Named other = new Named(name: 'Amazon')

        when:
        persisted.add(loaded)

        then: "equal but not identical"
        loaded == other
        persisted.contains(loaded)
        !persisted.contains(other)
    }

    void "a removed instance is not contained, and another that was added stays"() {
        given:
        PersistedInstances persisted = new PersistedInstances()
        Named amazon = new Named(name: 'Amazon')
        Named nile = new Named(name: 'Nile')
        persisted.add(amazon)
        persisted.add(nile)

        when:
        persisted.remove(amazon)

        then:
        !persisted.contains(amazon)
        persisted.contains(nile)
    }

    void "adding an instance twice keeps it contained, and removing it once removes it"() {
        given:
        PersistedInstances persisted = new PersistedInstances()
        Named amazon = new Named(name: 'Amazon')

        when:
        persisted.add(amazon)
        persisted.add(amazon)
        persisted.remove(amazon)

        then:
        !persisted.contains(amazon)
    }

    void "null is ignored"() {
        given:
        PersistedInstances persisted = new PersistedInstances()

        when:
        persisted.add(null)
        persisted.remove(null)

        then:
        !persisted.contains(null)
        noExceptionThrown()
    }

    void "an instance that nothing refers to any more is let go"() {
        given:
        PersistedInstances persisted = new PersistedInstances()
        List<Named> kept = (1..50).collect { new Named(name: "n$it") }
        kept.each { persisted.add(it) }
        (1..2000).each { persisted.add(new Named(name: "gone$it")) }

        when: "memory is collected and the registry is used again"
        10.times {
            System.gc()
            persisted.add(kept[0])
            Thread.sleep(20)
        }

        then: "the instances that are still referenced are contained"
        kept.every { persisted.contains(it) }
    }
}
