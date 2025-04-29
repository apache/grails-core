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
package grails.validation

import spock.lang.Issue
import spock.lang.Specification

class SerializableValidateableSpec extends Specification {

    @Issue('apache/grails-core#9986')
    void "test serialization"() {
        given:
        def p = new Person(firstName: 'Jeff', lastName: 'Brown')

        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)

        when:
        oos.writeObject(p)
        oos.flush()

        def bis = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bis)
        def p2 = ois.readObject()

        then:
        p2 instanceof Person
        p2.firstName == 'Jeff'
        p2.lastName == 'Brown'
    }
}

class Person implements Serializable, Validateable {
    String firstName
    String lastName
}