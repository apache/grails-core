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
package grails.testing.spock

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class OnceBeforeSpec extends Specification {

    @Shared
    int setupSpecCounter = 0

    @Shared
    int setupCounter = 0

    @Shared
    int onceBeforeCounter = 0

    @Shared
    int anotherOnceBeforeCounter = 0

    void setupSpec() {
        setupSpecCounter++
    }

    void setup() {
        setupCounter++
    }

    @OnceBefore
    void someOnceBeforeMethod() {
        onceBeforeCounter++
    }

    @OnceBefore
    void someOtherOnceBeforeMethod() {
        anotherOnceBeforeCounter++
    }

    void 'first test'() {
        expect:
        setupSpecCounter == 1
        setupCounter == 1
        onceBeforeCounter == 1
        anotherOnceBeforeCounter == 1
    }

    void 'second test'() {
        expect:
        setupSpecCounter == 1
        setupCounter == 2
        onceBeforeCounter == 1
        anotherOnceBeforeCounter == 1
    }

    void 'third test'() {
        expect:
        setupSpecCounter == 1
        setupCounter == 3
        onceBeforeCounter == 1
        anotherOnceBeforeCounter == 1
    }
}
