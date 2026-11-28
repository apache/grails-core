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
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.hibernate.criterion.Restrictions
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14370')
class HibernateCriterionInJunctionSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([JunctionAuthor])
    }

    void setup() {
        ['author1', 'author2', 'author3'].each { new JunctionAuthor(authorName: it).save() }
        manager.session.flush()
        manager.session.clear()
    }

    void "a Hibernate criterion added inside or is part of the disjunction"() {
        given:
        Long id1 = JunctionAuthor.findByAuthorName('author1').id

        expect:
        JunctionAuthor.createCriteria().list {
            or {
                add Restrictions.eq('authorName', 'author2')
                eq 'id', id1
            }
            order 'authorName'
        }*.authorName == ['author1', 'author2']
    }

    void "a Hibernate criterion added inside and is part of the conjunction"() {
        expect:
        JunctionAuthor.createCriteria().list {
            and {
                add Restrictions.like('authorName', 'author%')
                ne 'authorName', 'author3'
            }
            order 'authorName'
        }*.authorName == ['author1', 'author2']
    }

    void "a Hibernate criterion added inside not is negated"() {
        expect:
        JunctionAuthor.createCriteria().list {
            not {
                add Restrictions.eq('authorName', 'author1')
            }
            order 'authorName'
        }*.authorName == ['author2', 'author3']
    }

    void "add returns the Criteria at the top level, so calls chain, and the criterion it added inside a junction"() {
        expect:
        JunctionAuthor.createCriteria().list {
            add(Restrictions.like('authorName', 'author%')).add(Restrictions.ne('authorName', 'author3'))
            order 'authorName'
        }*.authorName == ['author1', 'author2']

        when:
        def added = []
        def eq1 = Restrictions.eq('authorName', 'author1')
        def eq3 = Restrictions.eq('authorName', 'author3')
        List found = JunctionAuthor.createCriteria().list {
            or {
                added << add(eq1)
                added << add(eq3)
            }
            order 'authorName'
        }

        then: 'each call returns the criterion it added, and both are part of the disjunction'
        added[0].is(eq1)
        added[1].is(eq3)
        found*.authorName == ['author1', 'author3']
    }

    void "the result of add at the top level is the Criteria, so list works on it"() {
        expect:
        JunctionAuthor.createCriteria().add(Restrictions.like('authorName', 'author%'))
                .add(Restrictions.ne('authorName', 'author3')).addOrder(org.hibernate.criterion.Order.asc('authorName'))
                .list()*.authorName == ['author1', 'author2']
    }

    void "a Hibernate criterion added at the top level still applies"() {
        expect:
        JunctionAuthor.createCriteria().list {
            add Restrictions.eq('authorName', 'author3')
        }*.authorName == ['author3']
    }
}

@Entity
class JunctionAuthor {
    String authorName
}
