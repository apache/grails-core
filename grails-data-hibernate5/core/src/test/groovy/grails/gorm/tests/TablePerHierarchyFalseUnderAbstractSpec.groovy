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
class TablePerHierarchyFalseUnderAbstractSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(TphAbstractBase, TphPerson, TphMan, TphTeam)
    }

    void "a subclass that sets tablePerHierarchy false is joined to an abstract base class"() {
        given:
        TphTeam team = new TphTeam().save(flush: true)
        new TphMan(firstName: 'a', weight: 70L, team: team).save(flush: true)
        session.clear()

        expect: 'the association to a deeper subclass loads without a discriminator column'
        TphTeam.get(team.id).men.size() == 1
        TphMan.list()*.firstName == ['a']
        TphPerson.list()*.weight == [70L]
    }
}

@Entity
abstract class TphAbstractBase {
    Long id
}

@Entity
class TphPerson extends TphAbstractBase {
    Long weight

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class TphMan extends TphPerson {
    String firstName
    TphTeam team
}

@Entity
class TphTeam {
    Long id

    static hasMany = [men: TphMan]
}
