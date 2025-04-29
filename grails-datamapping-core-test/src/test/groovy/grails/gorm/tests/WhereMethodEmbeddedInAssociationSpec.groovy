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
package grails.gorm.tests

import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import spock.lang.Shared

@ApplyDetachedCriteriaTransform
class WhereMethodEmbeddedInAssociationSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    @Shared
    def gcl

    void setupSpec() {
        gcl = new GroovyClassLoader()

        gcl.parseClass('''
import org.apache.grails.data.testing.tck.domains.*
import grails.gorm.annotation.*
import grails.persistence.*
import grails.gorm.DetachedCriteria

@Entity
class Address {
    String country
    String city
}

@Entity
class Contact {
    Address address

    static embedded = ['address']
    

}

@Entity
class Partner {
    Contact contact

    static DetachedCriteria<Partner> cityNameILike(String str) {
        where {
            contact.address.city =~ "$str"
        }
    }
}
''')

        def Partner = this.gcl.loadClass("Partner")
        def Contact = this.gcl.loadClass("Contact")
        def Address = this.gcl.loadClass("Address")

        manager.domainClasses << Partner
        manager.domainClasses << Contact
        manager.domainClasses << Address
    }

    def setup() {
        FieldEntityAccess.clearReflectors()
    }

    def "Test error when using embedded domain property of an association"() {
        when: "A an unknown domain class property of an association is referenced"
        def Partner = this.gcl.loadClass("Partner")
        def criteria = Partner.cityNameILike("Paris")

        then:
        criteria.list() == []
    }
}
