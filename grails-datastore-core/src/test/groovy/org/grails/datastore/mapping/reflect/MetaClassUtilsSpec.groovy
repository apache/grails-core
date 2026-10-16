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

class MetaClassUtilsSpec extends Specification {

    private final MetaClassRegistry registry = GroovySystem.metaClassRegistry

    void cleanup() {
        registry.removeMetaClass(Target)
    }

    void "getExpandoMetaClass registers an initialised ExpandoMetaClass permanently"() {
        when:
        ExpandoMetaClass emc = MetaClassUtils.getExpandoMetaClass(Target)

        then:
        emc != null
        emc.initialized
        registry.getMetaClass(Target).is(emc)

        and: 'a second call returns the same registered instance'
        MetaClassUtils.getExpandoMetaClass(Target).is(emc)
    }

    void "getExpandoMetaClass replaces a non-expando meta class"() {
        given:
        registry.removeMetaClass(Target)
        registry.setMetaClass(Target, new MetaClassImpl(Target))

        when:
        ExpandoMetaClass emc = MetaClassUtils.getExpandoMetaClass(Target)

        then:
        emc != null
        registry.getMetaClass(Target).is(emc)

        when: 'a method is added through it'
        emc.greet = { -> 'hello' }

        then: 'it is visible on instances'
        new Target().greet() == 'hello'
    }

    static class Target {
    }
}
