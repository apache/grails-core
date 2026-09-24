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
package grails.plugin.geb

import spock.lang.Specification

class ThreadLocalPropertyScopeSpec extends Specification {

    private static final String KEY = 'grails.geb.ThreadLocalPropertyScopeSpec'

    void 'a Groovy closure body reads the overridden property without re-entering the lookup'() {
        // Groovy 6 reads a system property while dispatching the closure call, so a lookup that
        // itself went through Groovy recursed until a StackOverflowError (#16157).
        expect:
        ThreadLocalPropertyScope.withProperty(KEY, 'overridden') { System.getProperty(KEY) } == 'overridden'

        and: 'the override is gone once the body returns'
        System.getProperty(KEY) == null
    }

    void 'the override is restored to the previous value and stays on the calling thread'() {
        given:
        System.setProperty(KEY, 'original')
        String seenOnOtherThread = null

        when:
        String seenInside = ThreadLocalPropertyScope.withProperty(KEY, 'overridden') {
            Thread other = new Thread({ seenOnOtherThread = System.getProperty(KEY) } as Runnable)
            other.start()
            other.join()
            System.getProperty(KEY)
        }

        then:
        seenInside == 'overridden'
        seenOnOtherThread == 'original'
        System.getProperty(KEY) == 'original'

        cleanup:
        System.clearProperty(KEY)
    }
}
