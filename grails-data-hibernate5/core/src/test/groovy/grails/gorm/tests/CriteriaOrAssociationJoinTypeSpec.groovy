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

import org.hibernate.criterion.CriteriaSpecification
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore

/**
 * An association block that names its join type keeps it: a block that asks for a left join finds the entities without
 * the association, whether it is a branch of an {@code or}, or follows a block in one that joined it with a left join
 * because it is a branch of the {@code or}.
 */
@Rollback
class CriteriaOrAssociationJoinTypeSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(JoinParent, JoinChild)

    private void createParents() {
        new JoinParent(name: 'with', child: new JoinChild(name: 'kid')).save(flush: true)
        new JoinParent(name: 'other', child: new JoinChild(name: 'stranger')).save(flush: true)
        new JoinParent(name: 'orphan').save(flush: true)
    }

    void 'a block that asks for a left join and is a branch of or keeps the entities without the association'() {
        given:
        createParents()

        when:
        def found = JoinParent.createCriteria().list {
            or {
                child(CriteriaSpecification.LEFT_JOIN) { isNull('name') }
                eq('name', 'nobody')
            }
        }

        then:
        found*.name == ['orphan']
    }

    void 'a later block that asks for a left join keeps the entities without the association'() {
        given:
        createParents()

        when:
        def found = JoinParent.createCriteria().list {
            or {
                child { eq('name', 'kid') }
                eq('name', 'orphan')
            }
            child(CriteriaSpecification.LEFT_JOIN) { isNull('name') }
        }

        then:
        found*.name == ['orphan']
    }
}

@Entity
class JoinParent {

    String name
    JoinChild child

    static constraints = {
        child(nullable: true)
    }
}

@Entity
class JoinChild {

    String name
}
