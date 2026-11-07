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

import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.IgnoreIf
import spock.lang.Issue

/**
 * The two answers to the question of issue 16889, as tests that are switched off until it is decided: do repeated
 * blocks of one association share a join, or does each block get its own?
 *
 * <p>The environment variable {@code ALIAS_BLOCK_DECISION} set to {@code shared} or {@code per-block} turns one
 * group on, so that either can be tried. When the question is decided, {@link #DECISION} becomes that literal, the
 * group of the other answer is deleted and the group that stays moves into {@code AssociationSortSpec}.</p>
 *
 * <p>An owner has a score in each of two rounds, so a query that restricts the round of the scores in two blocks
 * of one association matches the owner only if each block has a join of its own.</p>
 */
@Issue('https://github.com/apache/grails-core/issues/16889')
class AssociationBlockJoinDecisionSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    static final String DECISION = System.getenv('ALIAS_BLOCK_DECISION')

    void setupSpec() {
        manager.registerDomainClasses(AssociationSortOwner, AssociationSortScore, AssociationSortPet)
    }

    void setup() {
        [Ann: [1, 9], Bob: [9, 1]].each { ownerName, scores ->
            final owner = new AssociationSortOwner(name: ownerName)
            owner.addToScores(round: 'first', score: scores[0])
            owner.addToScores(round: 'second', score: scores[1])
            owner.save(failOnError: true)
            new AssociationSortPet(name: "${ownerName}'s pet", owner: owner).save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'shared' })
    void 'shared: two blocks of one association restrict one join, so no pet has a score in both rounds'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            owner {
                scores {
                    eq('round', 'second')
                }
            }
        }

        then:
        results.isEmpty()
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'shared' })
    void 'shared: an order in the second of two blocks has no rows to sort, because the blocks share a join'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            owner {
                scores {
                    eq('round', 'second')
                }
                order('scores.score', 'asc')
            }
        }

        then:
        results.isEmpty()
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'shared' })
    void 'shared: two blocks that declare different aliases for one path still restrict one join'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                createAlias('scores', 'first')
                eq('first.round', 'first')
            }
            owner {
                createAlias('scores', 'second')
                eq('second.round', 'second')
                order('scores.score')
            }
        }

        then: 'what the branch returns today: the aliases of the two blocks name one join, so no pet matches'
        results.isEmpty()
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'per-block' })
    void 'per block: an order in the second of two blocks of an association sorts #direction by the joins of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            owner {
                scores {
                    eq('round', 'second')
                }
                order('scores.score', direction)
            }
        }

        then: 'the order does not sort by the scores that the first block joins'
        results*.name == expected

        where:
        direction || expected
        'asc'     || ["Bob's pet", "Ann's pet"]
        'desc'    || ["Ann's pet", "Bob's pet"]
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'per-block' })
    void 'per block: an order in the first of two blocks of an association sorts by the joins of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
                order('scores.score')
            }
            owner {
                scores {
                    eq('round', 'second')
                }
            }
        }

        then:
        results*.name == ["Ann's pet", "Bob's pet"]
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'per-block' })
    void 'per block: an order in a nested block of the second of two blocks of an association sorts by the join of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            owner {
                scores {
                    eq('round', 'second')
                    order('score')
                }
            }
        }

        then:
        results*.name == ["Bob's pet", "Ann's pet"]
    }

    @IgnoreIf({ AssociationBlockJoinDecisionSpec.DECISION != 'per-block' })
    void 'per block: an order by a path in the second of two blocks that each declare an alias for it sorts by the join of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                createAlias('scores', 'first')
                eq('first.round', 'first')
            }
            owner {
                createAlias('scores', 'second')
                eq('second.round', 'second')
                order('scores.score')
            }
        }

        then:
        results*.name == ["Bob's pet", "Ann's pet"]
    }
}
