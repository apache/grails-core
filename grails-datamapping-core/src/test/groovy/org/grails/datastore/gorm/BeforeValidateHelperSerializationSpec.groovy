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

package org.grails.datastore.gorm

import org.grails.datastore.gorm.support.BeforeValidateHelper
import spock.lang.Specification


class BeforeValidateHelperSerializationSpec extends Specification {

    void "test serializing an instance of BeforeValidateHelper"() {
        given:
        def helper = new BeforeValidateHelper()

        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)

        when:
        oos.writeObject(helper)
        oos.flush()

        def bis = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bis)
        def helper2 = ois.readObject()

        then:
        helper2 instanceof BeforeValidateHelper
    }

    void "test serializing an instance which references BeforeValidateHelper"() {
        given:
        def obj = new SomethingThatReferencesBeforeValidateHelper(firstName: 'Jeff', lastName: 'Brown')

        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)

        when:
        oos.writeObject(obj)
        oos.flush()

        def bis = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bis)
        def obj2 = ois.readObject()

        then:
        obj2 instanceof SomethingThatReferencesBeforeValidateHelper
        obj2.firstName == 'Jeff'
        obj2.lastName == 'Brown'
    }
}

class SomethingThatReferencesBeforeValidateHelper implements Serializable {
    String firstName
    String lastName
    private BeforeValidateHelper helper = new BeforeValidateHelper()
}
