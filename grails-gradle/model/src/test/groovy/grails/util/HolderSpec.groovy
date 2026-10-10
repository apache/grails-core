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

class HolderSpec extends Specification {

    void 'get returns null before anything is set'() {
        given:
        Holder<String> holder = new Holder<>('test')

        expect:
        holder.get() == null
    }

    void 'set then get returns the stored value'() {
        given:
        Holder<String> holder = new Holder<>('test')

        when:
        holder.set('hello')

        then:
        holder.get() == 'hello'
    }

    void 'set with null clears the value'() {
        given:
        Holder<String> holder = new Holder<>('test')
        holder.set('hello')

        when:
        holder.set(null)

        then:
        holder.get() == null
    }

    void 'get(mappedOnly=true) does not fall back to the singleton value when unmapped'() {
        given:
        Holder<String> holder = new Holder<>('test')
        holder.set('hello')

        expect:
        holder.get(true) == 'hello'
    }

}
