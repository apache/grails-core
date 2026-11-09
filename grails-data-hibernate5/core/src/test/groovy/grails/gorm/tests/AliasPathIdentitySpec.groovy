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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.query.Query
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.PendingFeature

/**
 * An association is one join however a query reaches it: the identity of a join is the path of its association from the
 * root, and not the string that createAlias was given, the name of the alias, or the position of the statement.
 */
class AliasPathIdentitySpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([PiJudge, PiScore, PiOwner, PiPet, PiRevision, PiGroup, PiDoc])
    }

    void setup() {
        PiJudge j1 = new PiJudge(name: 'J1').save(failOnError: true)
        PiJudge j2 = new PiJudge(name: 'J2').save(failOnError: true)
        PiOwner a = new PiOwner(name: 'A')
        a.addToScores(new PiScore(round: 'first', score: 1, judge: j1))
        a.addToScores(new PiScore(round: 'second', score: 9, judge: j2))
        a.save(failOnError: true)
        PiOwner b = new PiOwner(name: 'B')
        b.addToScores(new PiScore(round: 'first', score: 9, judge: j2))
        b.addToScores(new PiScore(round: 'second', score: 1, judge: j1))
        b.save(failOnError: true)
        new PiPet(name: 'Ann', owner: a).save(failOnError: true)
        new PiPet(name: 'Bob', owner: b).save(failOnError: true)
        PiRevision m = new PiRevision(label: 'm').save(failOnError: true)
        PiRevision r = new PiRevision(label: 'r').save(failOnError: true)
        PiGroup gm = new PiGroup(name: 'gm', revision: m).save(failOnError: true)
        PiGroup gr = new PiGroup(name: 'gr', revision: r).save(failOnError: true)
        new PiDoc(name: 'doc1', group: gm, revision: r).save(failOnError: true)
        new PiDoc(name: 'doc2', group: gm, revision: m).save(failOnError: true)
        new PiDoc(name: 'doc3', group: gr, revision: r).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
    }

    void 'a block of a to-one association inside projections is a block that shares the join of the block at the root'() {
        when:
        final names = PiPet.withCriteria {
            owner {
                eq('name', 'A')
            }
            projections {
                owner {
                    property('name')
                }
            }
        }

        then: 'the projected name is the name of the owner that the block at the root restricts'
        names == ['A']
    }

    void 'a block of a collection inside projections shares the join of the block at the root'() {
        when:
        final scores = PiPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            projections {
                owner {
                    scores {
                        property('score')
                    }
                }
            }
        }

        then: 'the projected score is the score that the block restricts, and not each score of the owner'
        scores.sort() == [1, 9]
    }

    void 'one join spelled several ways in a query is one join'() {
        when:
        final rows = PiPet.withCriteria {
            createAlias('owner', 'owner')
            createAlias('owner.scores', 'owner_scores', 1)
            createAlias('owner_scores.judge', 'owner_scores_judge')
            createAlias('owner.scores.judge', 'owner_scores_judge2')
            eq('name', 'Ann')
            eq('owner_scores.round', 'first')
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            projections {
                property('owner_scores_judge.name')
                property('owner_scores_judge2.name')
            }
        }

        then: 'every column comes from the one score of the first round, so the judge is the same'
        rows == [['J1', 'J1']]
    }

    void 'the identity of a join is its full path, and not the name of its last segment'() {
        when:
        final docs = PiDoc.withCriteria {
            createAlias('group.revision', 'masterRevision')
            eq('masterRevision.label', 'm')
            revision {
                eq('label', 'r')
            }
        }

        then: 'the revision of the group and the revision of the document are two joins'
        docs*.name == ['doc1']
    }

    void 'a block inside a subquery correlated with an alias of the outer query restricts the joins of its own query'() {
        when:
        final owners = PiOwner.withCriteria {
            createAlias('scores', 's')
            eq('s.round', 'first')
            exists(new DetachedCriteria(PiScore).build {
                eqProperty('id', 's.id')
                judge {
                    eq('name', 'J1')
                }
            }.id())
        }

        then: 'only the first round score of owner A has the judge J1'
        owners*.name == ['A']
    }

    @PendingFeature(reason = 'the root alias of a subquery is #16821: this.id names the root of the subquery until it lands')
    void 'a block inside a subquery correlated with this.id restricts the joins of its own query'() {
        when:
        final owners = PiOwner.withCriteria {
            exists(new DetachedCriteria(PiOwner).build {
                eqProperty('id', 'this.id')
                scores {
                    eq('round', 'first')
                    eq('score', 1)
                }
            }.id())
        }

        then:
        owners*.name == ['A']
    }

    void 'an order by an alias-qualified path sorts the same wherever it sits and whichever overload declares it'() {
        when:
        final sorted = PiPet.withCriteria(closure)

        then:
        sorted*.name == ['Bob', 'Ann']

        where:
        where | closure
        'the root, order(String, String)' | { createAlias('owner', 'owner'); createAlias('owner.scores', 'owner_scores'); eq('owner_scores.round', 'first'); order('owner_scores.score', 'desc') }
        'the root, order(Query.Order)' | { createAlias('owner', 'owner'); createAlias('owner.scores', 'owner_scores'); eq('owner_scores.round', 'first'); order(new Query.Order('owner_scores.score', Query.Order.Direction.DESC)) }
        'inside or, order(String, String)' | { createAlias('owner', 'owner'); createAlias('owner.scores', 'owner_scores'); or { eq('owner_scores.round', 'first'); order('owner_scores.score', 'desc') } }
        'inside and, order(String, String)' | { createAlias('owner', 'owner'); createAlias('owner.scores', 'owner_scores'); and { eq('owner_scores.round', 'first'); order('owner_scores.score', 'desc') } }
    }

    void 'an order in the second build of a detached criteria that the first build declared the alias for'() {
        when:
        final criteria = new DetachedCriteria(PiPet).build {
            createAlias('owner', 'owner')
            createAlias('owner.scores', 'owner_scores')
            eq('owner_scores.round', 'first')
        }.build {
            order('owner_scores.score', 'desc')
        }

        then:
        criteria.list()*.name == ['Bob', 'Ann']
    }

    void 'the rows depend on the order of the statements only where Hibernate 5 cannot join a path twice'() {
        given:
        List<Closure> steps = [
            { createAlias('owner', 'owner') },
            { createAlias('owner.scores', 'owner_scores') },
            { eq('owner_scores.round', 'first') },
            { owner { scores { eq('round', 'first') } } },
        ]
        List<List<Closure>> orders = steps.permutations() as List

        when:
        Map<List<Integer>, List<String>> rows = [:]
        orders.each { List<Closure> order ->
            def ids = order.collect { steps.indexOf(it) }
            def found
            try {
                found = PiPet.withCriteria {
                    order.each { Closure step -> step.delegate = delegate; step.resolveStrategy = Closure.DELEGATE_FIRST; step.call() }
                    projections { property('name') }
                }.sort()
            } catch (Throwable t) {
                found = ["${t.class.simpleName}: ${t.message?.readLines()?.first()?.take(80)}".toString()]
            }
            rows[ids] = found
        }

        then: 'an order returns both pets, or is rejected when an alias is used before it is declared and a block already joins its path'
        rows.values().every { it == ['Ann', 'Bob'] || it.first().contains('is used before it is') }
        rows.values().count { it == ['Ann', 'Bob'] } > 12
    }
}

@Entity
class PiJudge {
    String name
}

@Entity
class PiScore {
    String round
    Integer score
    PiJudge judge
}

@Entity
class PiOwner {
    String name

    static hasMany = [scores: PiScore]
}

@Entity
class PiPet {
    String name
    PiOwner owner
}

@Entity
class PiRevision {
    String label
}

@Entity
class PiGroup {
    String name
    PiRevision revision
}

@Entity
class PiDoc {
    String name
    PiGroup group
    PiRevision revision
}
