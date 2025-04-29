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
import spock.lang.Issue

class InheritanceWithOneToManySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Group, Member, SubMember])
    }

    @Issue('GRAILS-9010')
    void "Test that a one-to-many cascades to an association featuring inheritance"() {
        when: "A domain model with an association featuring inheritance is saved"
        def group = new Group(name: "my group")
        def subMember = new SubMember(name: "my name", extraName: "extra name", externalId: 'blah')
        group.addToMembers subMember
        group.save(failOnError: true, flush: true)
        manager.session.clear()

        then: "The association is correctly saved"
        Group.count() == 1
        SubMember.count() == 1
    }
}

@Entity
class Group {
    Long id
    String name
    static hasMany = [members: Member]
    Collection members
}

@Entity
class Member {
    Long id
    String name
    String externalId
}

@Entity
class SubMember extends Member {
    String extraName
}