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

package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId

/**
 * @author Graeme Rocher
 */
class BasicArraySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Data])
    }

    void "Test that arrays are saved correctly"() {
        when: "An entity with an array is saved"
        Data data = new Data(str: "foo", strArray: ["foo", "bar"] as String[]).save(flush: true, validate: false)
        manager.session.clear()
        data = Data.findByStr("foo")

        then: "The array is saved correct"
        data.str == "foo"
        data.strArray[0] == "foo"
        data.strArray[1] == 'bar'
    }

    void "Test that arrays of convertible properties are saved correctly"() {
        when: "An entity with an array is saved"
        Data data = new Data(str: "bar", locArray: [Locale.US, Locale.CANADA_FRENCH] as Locale[]).save(flush: true, validate: false)
        manager.session.clear()
        data = Data.findByStr("bar")

        then: "The array is saved correct"
        data.str == "bar"
        data.locArray[0] == Locale.US
        data.locArray[1] == Locale.CANADA_FRENCH
    }

    void "Test that byte arrays are saved as binary"() {
        when: "An entity with an array is saved"
        Data data = new Data(str: "baz", byteArray: 'hello'.bytes).save(flush: true, validate: false)
        manager.session.clear()
        data = Data.findByStr("baz")
        Document dbo = data.dbo

        then: "The array is saved correct"
        data.str == "baz"
        data.byteArray == 'hello'.bytes
        dbo.byteArray.data == 'hello'.bytes
    }
}

@Entity
class Data {

    ObjectId id
    String str
    String[] strArray
    Locale[] locArray
    byte[] byteArray

    @Override
    String toString() {
        "Data{id=$id, str='$str', strArray=${(strArray == null ? null : Arrays.asList(strArray))}, locArray=${(locArray == null ? null : Arrays.asList(locArray))}}"
    }
}
