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

package org.grails.gorm.graphql.binding

import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification

class DataBinderNotFoundExceptionSpec extends Specification {

    void "test the message when constructed with a class"() {
        expect:
        new DataBinderNotFoundException(String).message == 'A GraphQL data binder could not be found for java.lang.String'
    }

    void "test the message when constructed with a persistent entity"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            getJavaClass() >> String
        }

        expect:
        new DataBinderNotFoundException(entity).message == 'A GraphQL data binder could not be found for java.lang.String'
    }
}
