/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.internal

import jakarta.persistence.LockModeType

import spock.lang.Specification
import spock.lang.Unroll

class RefreshLockArgumentsSpec extends Specification {

    @Unroll
    void "lockModeFrom(#args) resolves #expected"() {
        expect:
        RefreshLockArguments.lockModeFrom(args) == expected

        where:
        args                                          | expected
        null                                           | null
        [:]                                            | null
        [lock: false]                                  | null
        [lock: true]                                   | LockModeType.PESSIMISTIC_WRITE
        [lock: 'true']                                 | LockModeType.PESSIMISTIC_WRITE
        [lock: 'false']                                | null
        [lock: LockModeType.PESSIMISTIC_READ]           | LockModeType.PESSIMISTIC_READ
        [lock: 'PESSIMISTIC_READ']                      | LockModeType.PESSIMISTIC_READ
        [lock: LockModeType.NONE]                       | null
    }

    void "lockModeFrom rejects a value that is neither a boolean nor a recognized lock mode"() {
        when:
        RefreshLockArguments.lockModeFrom([lock: 42])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "The 'lock' argument must be a boolean or a jakarta.persistence.LockModeType but was an instance of java.lang.Integer"

        when:
        RefreshLockArguments.lockModeFrom([lock: 'bogus'])

        then:
        def nameException = thrown(IllegalArgumentException)
        nameException.message == "The 'lock' argument must be a boolean or a jakarta.persistence.LockModeType but was 'bogus'"

        when: 'a blank value, which a caller reading the mode from configuration or a parameter can supply'
        RefreshLockArguments.lockModeFrom([lock: ' '])

        then: 'it is rejected rather than read as no lock, which would hand back an unlocked instance'
        def blankException = thrown(IllegalArgumentException)
        blankException.message == "The 'lock' argument must be a boolean or a jakarta.persistence.LockModeType but was ''"
    }

    @Unroll
    void "lockTypeFrom(#args) resolves #expected"() {
        expect:
        RefreshLockArguments.lockTypeFrom(args) == expected

        where:
        args                                          | expected
        null                                           | LockModeType.PESSIMISTIC_WRITE
        [:]                                            | LockModeType.PESSIMISTIC_WRITE
        [type: LockModeType.PESSIMISTIC_WRITE]          | LockModeType.PESSIMISTIC_WRITE
        [type: LockModeType.PESSIMISTIC_READ]           | LockModeType.PESSIMISTIC_READ
        [type: 'pessimistic_read']                      | LockModeType.PESSIMISTIC_READ
    }

    void "lockTypeFrom rejects NONE explicitly"() {
        when:
        RefreshLockArguments.lockTypeFrom([type: LockModeType.NONE])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "The 'type' argument must name a lock but was NONE"
    }

    void "lockTypeFrom rejects a value that is not a lock mode"() {
        when:
        RefreshLockArguments.lockTypeFrom([type: true])

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "The 'type' argument must be a jakarta.persistence.LockModeType but was an instance of java.lang.Boolean"
    }

    @Unroll
    void "refreshRequested(#args) is #expected"() {
        expect:
        RefreshLockArguments.refreshRequested(args) == expected

        where:
        args               | expected
        null               | false
        [:]                | false
        [refresh: false]   | false
        [refresh: true]    | true
        [refresh: 'true']  | true
        [refresh: 'false'] | false
    }

    @Unroll
    void "refreshRequested rejects #args rather than silently treating it as no refresh"() {
        when:
        RefreshLockArguments.refreshRequested(args)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == message

        where:
        args               | message
        [refresh: 'yes']   | "The 'refresh' argument must be a boolean but was 'yes'"
        [refresh: '']      | "The 'refresh' argument must be a boolean but was ''"
        [refresh: ' ']     | "The 'refresh' argument must be a boolean but was ''"
        [refresh: 1]       | "The 'refresh' argument must be a boolean but was an instance of java.lang.Integer"
    }

    @Unroll
    void "pessimistic(#lockMode) is #expected"() {
        expect:
        RefreshLockArguments.pessimistic(lockMode) == expected

        where:
        lockMode                                     | expected
        LockModeType.PESSIMISTIC_READ                 | true
        LockModeType.PESSIMISTIC_WRITE                | true
        LockModeType.PESSIMISTIC_FORCE_INCREMENT      | true
        LockModeType.OPTIMISTIC                       | false
        LockModeType.OPTIMISTIC_FORCE_INCREMENT       | false
        LockModeType.NONE                             | false
    }
}
