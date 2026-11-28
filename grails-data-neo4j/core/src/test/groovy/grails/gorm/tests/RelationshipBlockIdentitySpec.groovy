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
import grails.neo4j.Relationship
import grails.neo4j.mapping.MappingBuilder
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import spock.lang.Issue

/**
 * The criteria of a block on an association to a relationship entity are about the relationship, so its identity
 * is the identity of the relationship entity, not the one of the node that owns the relationship.
 */
@Issue('https://github.com/apache/grails-core/pull/16836')
class RelationshipBlockIdentitySpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(RelIdRoot, RelIdOwner, RelIdTarget, RelIdLink)
    }

    private Map seed() {
        RelIdTarget target = new RelIdTarget(name: 't').save(flush: true, failOnError: true)
        RelIdOwner withLink = new RelIdOwner(name: 'with').save(flush: true, failOnError: true)
        RelIdOwner without = new RelIdOwner(name: 'without').save(flush: true, failOnError: true)
        new RelIdLink(from: withLink, to: target, kind: 'a', total: 1L).save(flush: true, failOnError: true)
        new RelIdRoot(label: 'r-with', owner: withLink).save(flush: true, failOnError: true)
        new RelIdRoot(label: 'r-without', owner: without).save(flush: true, failOnError: true)
        RelIdTarget otherTarget = new RelIdTarget(name: 'other').save(flush: true, failOnError: true)
        RelIdRoot.withSession { it.clear() }
        [link: RelIdLink.list().find { it.kind == 'a' }, target: RelIdTarget.findByName('t'), otherTarget: RelIdTarget.findByName('other'),
         owner: RelIdOwner.findByName('with')]
    }

    void "an identity test on a relationship in a block uses the identity of the relationship entity"() {
        given: 'a native id root, a snowflake id owner, and a relationship with a native id'
        Map data = seed()
        assert data.link.id != null

        when:
        List<RelIdRoot> roots = RelIdRoot.createCriteria().list {
            owner {
                links {
                    idEq(data.link.id)
                }
            }
        }

        then:
        roots*.label == ['r-with']
    }

    void "an equality on the identity property of a relationship in a block uses the identity of the relationship entity"() {
        given:
        Map data = seed()

        expect:
        RelIdRoot.createCriteria().list {
            owner {
                links {
                    eq('id', data.link.id)
                }
            }
        }*.label == ['r-with']
    }

    void "a property of the relationship still compares as a property"() {
        given:
        seed()

        expect:
        RelIdRoot.createCriteria().list {
            owner {
                links {
                    eq('kind', 'a')
                }
            }
        }*.label == ['r-with']
    }

    void "an end of the relationship in a block is compared through the node the block bound for that end"() {
        given:
        Map data = seed()

        expect: 'the end that the link reaches and the end it starts from match, and another node does not'
        RelIdRoot.createCriteria().list { owner { links { eq('to', data.target) } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { eq('to', data.otherTarget) } } }.empty
        RelIdRoot.createCriteria().list { owner { links { eq('from', data.owner) } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { ne('to', data.otherTarget) } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { ne('to', data.target) } } }.empty
    }

    void "an end of the relationship in a block holds beside another criterion, in a disjunction and under a negation"() {
        given:
        Map data = seed()

        expect:
        RelIdRoot.createCriteria().list { owner { links { eq('to', data.target); eq('kind', 'a') } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { eq('to', data.target); eq('kind', 'b') } } }.empty
        RelIdRoot.createCriteria().list { or { owner { links { eq('to', data.target) } }; eq('label', 'r-without') } }*.label.sort() == ['r-with', 'r-without']
        RelIdRoot.createCriteria().list { or { owner { links { eq('to', data.otherTarget) } }; eq('label', 'r-without') } }*.label == ['r-without']
    }

    void "an end of a relationship is compared when the relationship is the root of the query"() {
        given:
        Map data = seed()

        expect:
        RelIdLink.createCriteria().list { eq('to', data.target) }*.kind == ['a']
        RelIdLink.createCriteria().list { eq('to', data.otherTarget) }.empty
        RelIdLink.createCriteria().list { eq('from', data.owner) }*.kind == ['a']
    }

    void "a numeric property of the relationship is qualified with the relationship variable"() {
        given:
        seed()

        expect:
        RelIdRoot.createCriteria().list {
            owner {
                links {
                    eq('total', 1L)
                }
            }
        }*.label == ['r-with']
        RelIdRoot.createCriteria().list {
            owner {
                links {
                    gt('total', 5L)
                }
            }
        }.empty
    }

    void "every comparison of a relationship property in a block goes through the relationship variable"() {
        given:
        seed()

        expect: 'a range, a list, a like and an isNull name the property of the relationship, not of a node'
        RelIdRoot.createCriteria().list { owner { links { between('total', 0L, 5L) } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { between('total', 2L, 5L) } } }.empty
        RelIdRoot.createCriteria().list { owner { links { 'in'('total', [1L, 9L]) } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { like('kind', 'a%') } } }*.label == ['r-with']
        RelIdRoot.createCriteria().list { owner { links { isNull('total') } } }.empty
        RelIdRoot.createCriteria().list { owner { links { isNotNull('total') } } }*.label == ['r-with']
    }
}

@Entity
class RelIdRoot {
    String label
    RelIdOwner owner
}

@Entity
class RelIdOwner {
    String name
    static hasMany = [links: RelIdLink]

    static mapping = MappingBuilder.node {
        id(generator: 'snowflake')
    }
}

@Entity
class RelIdTarget {
    String name
}

@Entity
class RelIdLink implements Relationship<RelIdOwner, RelIdTarget> {
    String kind
    Long total
}
