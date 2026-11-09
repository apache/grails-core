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
        RelIdRoot.withSession { it.clear() }
        [link: RelIdLink.list().find { it.kind == 'a' }]
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
