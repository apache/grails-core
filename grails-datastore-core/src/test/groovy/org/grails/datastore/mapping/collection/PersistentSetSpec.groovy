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
package org.grails.datastore.mapping.collection

import org.springframework.util.ReflectionUtils
import org.springframework.util.SerializationUtils
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by lari on 27/01/15.
 */
class PersistentSetSpec extends Specification {

    @Issue("GRAILS-11929")
    def "should support serialization after initialized"() {
        given:
        PersistentSet pset = new PersistentSet(String, null, ['a','b','c'] as Set)
        def initializedField = ReflectionUtils.findField(AbstractPersistentCollection, "initialized")
        ReflectionUtils.makeAccessible(initializedField)
        ReflectionUtils.setField(initializedField, pset, true)
        when:
        def psetSerialized = SerializationUtils.deserialize(SerializationUtils.serialize(pset))
        then:
        psetSerialized == ['a','b','c'] as Set
        psetSerialized == pset

    }

}
