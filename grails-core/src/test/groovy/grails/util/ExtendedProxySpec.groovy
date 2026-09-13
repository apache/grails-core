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

class ExtendedProxySpec extends Specification {

    void 'reading a proxy-declared property whose value is still null falls through to the (unset) adaptee and fails'() {
        given:
        ExtendedProxy proxy = new ExtendedProxy()

        when:
        proxy.getProperty('adaptee')

        then:
        // the null check in getProperty cannot distinguish "not cached" from "genuinely null",
        // so it always falls through to the adaptee lookup, which NPEs when no adaptee is set yet
        thrown(NullPointerException)
    }

    void 'the proxy-declared property snapshot is captured once at construction, so a later setAdaptee is not reflected by name'() {
        given:
        ExtendedProxy proxy = new ExtendedProxy()
        Wrapped wrapped = new Wrapped(name: 'Bob')
        proxy.setAdaptee(wrapped)

        when:
        // "adaptee" was captured as null in the constructor's propertyMap snapshot, so getProperty('adaptee')
        // still treats it as "not cached" and falls through to asking the (now real) adaptee for its own
        // "adaptee" property, which Wrapped does not have
        proxy.getProperty('adaptee')

        then:
        thrown(groovy.lang.MissingPropertyException)
    }

    void 'reading a property not declared by the proxy is delegated to the adaptee'() {
        given:
        ExtendedProxy proxy = new ExtendedProxy()
        proxy.setAdaptee(new Wrapped(name: 'Bob'))

        expect:
        proxy.getProperty('name') == 'Bob'
    }

    void 'setting a property not declared by the proxy is delegated to the adaptee'() {
        given:
        ExtendedProxy proxy = new ExtendedProxy()
        Wrapped wrapped = new Wrapped(name: 'Bob')
        proxy.setAdaptee(wrapped)

        when:
        proxy.setProperty('name', 'Judy')

        then:
        wrapped.name == 'Judy'
    }

    void 'setting a property the proxy itself declares is handled by the proxy'() {
        given:
        ExtendedProxy proxy = new ExtendedProxy()

        when:
        proxy.setAdaptee(new Wrapped(name: 'Bob'))

        then:
        proxy.getAdaptee().name == 'Bob'
    }
}

class Wrapped {
    String name
}
