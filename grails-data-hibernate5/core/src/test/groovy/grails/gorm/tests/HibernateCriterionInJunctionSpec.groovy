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
import org.hibernate.criterion.Restrictions
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14370')
class HibernateCriterionInJunctionSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(JunctionAuthor)
    }

    void setup() {
        ['author1', 'author2', 'author3'].each { new JunctionAuthor(authorName: it).save() }
        session.flush()
        session.clear()
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
