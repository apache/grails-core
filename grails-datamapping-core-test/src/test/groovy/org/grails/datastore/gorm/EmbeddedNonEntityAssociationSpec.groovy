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

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class EmbeddedNonEntityAssociationSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Being])
    }

    void "Test persistence of embedded entities"() {
        given:
        def i = new Being(name: "Bob", address: new ResidentialAddress(postCode: "30483"))

        i.save(flush: true)
        manager.session.clear()

        when:
        i = Being.findByName("Bob")

        then:
        i != null
        i.name == 'Bob'
        i.address != null
        i.address.postCode == '30483'
    }
}

@Entity
class Being {
    Long id
    String name
    ResidentialAddress address
    static embedded = ['address']

    static mapping = {
        name index: true
    }
}

class ResidentialAddress {
    String postCode
}
