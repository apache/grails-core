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

import java.util.function.Supplier

import spock.lang.Specification

class SupplierUtilSpec extends Specification {

    void 'memoized only invokes the delegate supplier once'() {
        given:
        int callCount = 0
        Supplier<String> actual = { -> callCount++; 'value' } as Supplier<String>
        Supplier<String> memoized = SupplierUtil.memoized(actual)

        when:
        String first = memoized.get()
        String second = memoized.get()

        then:
        first == 'value'
        second == 'value'
        callCount == 1
    }

    void 'memoizedNonEmpty caches a non-null, non-empty value'() {
        given:
        int callCount = 0
        Supplier<String> actual = { -> callCount++; 'value' } as Supplier<String>
        Supplier<String> memoized = SupplierUtil.memoizedNonEmpty(actual)

        when:
        String first = memoized.get()
        String second = memoized.get()

        then:
        first == 'value'
        second == 'value'
        callCount == 1
    }

    void 'memoizedNonEmpty does not cache a null value'() {
        given:
        int callCount = 0
        Supplier<String> actual = { -> callCount++; null } as Supplier<String>
        Supplier<String> memoized = SupplierUtil.memoizedNonEmpty(actual)

        when:
        memoized.get()
        memoized.get()

        then:
        callCount == 2
    }

    void 'memoizedNonEmpty does not cache an empty Optional'() {
        given:
        int callCount = 0
        Supplier<Optional<String>> actual = { -> callCount++; Optional.empty() } as Supplier<Optional<String>>
        Supplier<Optional<String>> memoized = SupplierUtil.memoizedNonEmpty(actual)

        when:
        memoized.get()
        memoized.get()

        then:
        callCount == 2
    }

    void 'memoizedNonEmpty caches a present Optional'() {
        given:
        int callCount = 0
        Supplier<Optional<String>> actual = { -> callCount++; Optional.of('value') } as Supplier<Optional<String>>
        Supplier<Optional<String>> memoized = SupplierUtil.memoizedNonEmpty(actual)

        when:
        Optional<String> first = memoized.get()
        Optional<String> second = memoized.get()

        then:
        first.get() == 'value'
        second.get() == 'value'
        callCount == 1
    }

}
