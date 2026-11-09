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

import grails.gorm.annotation.Entity
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14423')
class JoinedUnderPlainParentSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(PlainParentRoot, PlainParentPerson, PlainParentMan, PlainParentTeam)
    }

    void "an abstract root that extends a class that is not a domain class is joined to its subclass"() {
        given:
        PlainParentTeam team = new PlainParentTeam().save(flush: true)
        new PlainParentMan(firstName: 'a', weight: 70L, team: team).save(flush: true)
        session.clear()

        expect: 'the hierarchy has the tables of the joined strategy, and the subclass loads through a collection'
        PlainParentTeam.get(team.id).men*.firstName == ['a']
        PlainParentMan.list()*.firstName == ['a']
        PlainParentPerson.list()*.weight == [70L]
    }
}

abstract class PlainParent {
}

@Entity
abstract class PlainParentRoot extends PlainParent {
    Long id
}

@Entity
class PlainParentPerson extends PlainParentRoot {
    Long weight

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class PlainParentMan extends PlainParentPerson {
    String firstName
    PlainParentTeam team
}

@Entity
class PlainParentTeam {
    Long id

    static hasMany = [men: PlainParentMan]
}
