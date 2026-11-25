/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.testing.tck.tests

import grails.gorm.DetachedCriteria
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.CriteriaAnimal
import org.apache.grails.data.testing.tck.domains.CriteriaGrandparent
import org.apache.grails.data.testing.tck.domains.CriteriaLeaf
import org.apache.grails.data.testing.tck.domains.CriteriaMiddle
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * Shapes that were reported against criteria and detached criteria: blocks nested two deep, a self-referencing class
 * with a block on each of two associations, the same query composed from two detached criteria, and junctions without
 * criteria, which add nothing to the query they stand in. MongoDB has no association blocks.
 */
@IgnoreIf({ System.getProperty('mongodb.gorm.suite') == 'true' })
class CriteriaNestedShapesSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.domainClasses.addAll([CriteriaAnimal, CriteriaGrandparent, CriteriaMiddle, CriteriaLeaf])
    }

    /** two chains: p1 - m1 - l1 and p2 - m2 - l2, and a leaf l0 whose middle has no parent */
    private void seedChains() {
        // two unreferenced grandparents first, so that no grandparent shares an identifier with the middle it belongs to
        new CriteriaGrandparent(code: 'X1').save(failOnError: true)
        new CriteriaGrandparent(code: 'X2').save(failOnError: true)
        CriteriaGrandparent p1 = new CriteriaGrandparent(code: 'P1').save(failOnError: true)
        CriteriaGrandparent p2 = new CriteriaGrandparent(code: 'P2').save(failOnError: true)
        CriteriaMiddle m1 = new CriteriaMiddle(code: 'M1', parent: p1).save(failOnError: true)
        CriteriaMiddle m2 = new CriteriaMiddle(code: 'M2', parent: p2).save(failOnError: true)
        CriteriaMiddle m0 = new CriteriaMiddle(code: 'M0').save(failOnError: true)
        new CriteriaLeaf(code: 'L1', child: m1).save(failOnError: true)
        new CriteriaLeaf(code: 'L2', child: m2).save(failOnError: true)
        new CriteriaLeaf(code: 'L0', child: m0).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    /** sire and dam: Society Lady has sire Mr. Prospector and dam La Voyageuse, whose sire is Tentam */
    private void seedPedigree() {
        CriteriaAnimal prospector = new CriteriaAnimal(name: 'Mr. Prospector').save(failOnError: true)
        CriteriaAnimal tentam = new CriteriaAnimal(name: 'Tentam').save(failOnError: true)
        CriteriaAnimal fanfreluche = new CriteriaAnimal(name: 'Fanfreluche').save(failOnError: true)
        CriteriaAnimal voyageuse = new CriteriaAnimal(name: 'La Voyageuse', sire: tentam, dam: fanfreluche).save(failOnError: true)
        new CriteriaAnimal(name: 'Society Lady', sire: prospector, dam: voyageuse).save(failOnError: true)
        new CriteriaAnimal(name: 'Other', sire: tentam, dam: voyageuse).save(failOnError: true)
        new CriteriaAnimal(name: 'Lonely', sire: prospector).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void "a block nested two deep"() {
        given:
        seedChains()

        expect:
        CriteriaLeaf.createCriteria().list {
            child {
                parent {
                    eq('code', 'P1')
                }
            }
        }*.code == ['L1']
    }

    void "a block nested two deep beside a property in a disjunction"() {
        given:
        seedChains()

        expect:
        CriteriaLeaf.createCriteria().list {
            or {
                child {
                    parent {
                        eq('code', 'P2')
                    }
                }
                eq('code', 'L0')
            }
        }*.code.sort() == ['L0', 'L2']
    }

    void "a block on each of two associations of a self-referencing class, one of them two deep"() {
        given:
        seedPedigree()

        expect:
        CriteriaAnimal.createCriteria().list {
            sire {
                eq('name', 'Mr. Prospector')
            }
            dam {
                sire {
                    eq('name', 'Tentam')
                }
            }
        }*.name == ['Society Lady']
    }

    void "a block nested in a block, inside a conjunction, keeps its own join"() {
        given:
        seedPedigree()

        expect:
        CriteriaAnimal.createCriteria().list {
            and {
                sire {
                    eq('name', 'Mr. Prospector')
                }
                dam {
                    sire {
                        eq('name', 'Tentam')
                    }
                }
            }
        }*.name == ['Society Lady']
    }

    void "an empty junction in a nested block still requires the association"() {
        given:
        seedChains()

        expect:
        CriteriaLeaf.createCriteria().list {
            child {
                parent {
                    and {}
                }
            }
        }*.code.sort() == ['L1', 'L2']
    }

    /** the closure of a row takes no argument; a detached criteria hands its builder to the closure it builds from */
    private static List<String> detachedNames(Closure rows) {
        Closure adapted = { Object builder ->
            rows.delegate = delegate
            rows.resolveStrategy = Closure.DELEGATE_FIRST
            rows.call()
        }
        new DetachedCriteria(CriteriaAnimal).build(adapted).list()*.name.sort()
    }

    @Unroll
    void "the pedigree query #shape returns #expected"() {
        given:
        seedPedigree()

        expect: 'through criteria'
        CriteriaAnimal.createCriteria().list(closure)*.name.sort() == expected

        and: 'through a detached criteria, which builds its joins another way'
        detachedNames(closure) == expected

        where:
        shape                                    | closure                                                                                        || expected
        'sire then dam.sire in an and'           | { -> and { sire { eq('name', 'Mr. Prospector') }; dam { sire { eq('name', 'Tentam') } } } }    || ['Society Lady']
        'dam.sire then sire in an and'           | { -> and { dam { sire { eq('name', 'Tentam') } }; sire { eq('name', 'Mr. Prospector') } } }    || ['Society Lady']
        'sire then dam.sire, no and'             | { -> sire { eq('name', 'Mr. Prospector') }; dam { sire { eq('name', 'Tentam') } } }             || ['Society Lady']
        'dam.sire then sire, no and'             | { -> dam { sire { eq('name', 'Tentam') } }; sire { eq('name', 'Mr. Prospector') } }             || ['Society Lady']
        'sire or dam.sire'                       | { -> or { sire { eq('name', 'Mr. Prospector') }; dam { sire { eq('name', 'Tentam') } } } }     || ['Lonely', 'Other', 'Society Lady']
        'dam.sire or sire'                       | { -> or { dam { sire { eq('name', 'Tentam') } }; sire { eq('name', 'Mr. Prospector') } } }     || ['Lonely', 'Other', 'Society Lady']
        'sire and dam.sire in an or of ands'     | { -> or { and { sire { eq('name', 'Mr. Prospector') }; dam { sire { eq('name', 'Tentam') } } }; eq('name', 'Lonely') } } || ['Lonely', 'Society Lady']
        'dam.sire and an empty and beside sire'  | { -> and { sire { eq('name', 'Mr. Prospector') }; dam { sire { eq('name', 'Tentam') } }; and {} } } || ['Society Lady']
        'not dam.sire beside sire, keeping the join' | { -> sire { eq('name', 'Mr. Prospector') }; not { dam { sire { eq('name', 'Tentam') } } } }    || []
        'dam.sire twice in an and'               | { -> and { dam { sire { eq('name', 'Tentam') } }; dam { sire { ne('name', 'Fanfreluche') } } } }  || ['Other', 'Society Lady']
        'sire, then dam.sire twice'              | { -> and { sire { eq('name', 'Mr. Prospector') }; dam { sire { eq('name', 'Tentam') } }; dam { sire { ne('name', 'Nobody') } } } } || ['Society Lady']
        'dam.sire twice, no and'                 | { -> dam { sire { eq('name', 'Tentam') } }; dam { sire { ne('name', 'Nobody') } } }                || ['Other', 'Society Lady']
        'dam.sire, sire, dam.sire again'         | { -> dam { sire { eq('name', 'Tentam') } }; sire { eq('name', 'Mr. Prospector') }; dam { sire { ne('name', 'Nobody') } } } || ['Society Lady']
        'sire twice at the root'                 | { -> sire { eq('name', 'Mr. Prospector') }; sire { ne('name', 'Nobody') } }                        || ['Lonely', 'Society Lady']
        'dam.sire twice in an or'                | { -> or { dam { sire { eq('name', 'Tentam') } }; dam { sire { eq('name', 'Nobody') } } } }         || ['Other', 'Society Lady']
    }

    void "the same query composed from two detached criteria"() {
        given:
        seedPedigree()

        when:
        DetachedCriteria<CriteriaAnimal> criteria = new DetachedCriteria<>(CriteriaAnimal)
        criteria = criteria.build {
            sire {
                eq('name', 'Mr. Prospector')
            }
        }
        criteria = criteria.build {
            dam {
                sire {
                    eq('name', 'Tentam')
                }
            }
        }

        then:
        criteria.list()*.name == ['Society Lady']
        criteria.count() == 1
    }

    @Unroll
    void "#shape adds nothing to the query it stands in"() {
        given:
        seedPedigree()

        expect:
        CriteriaAnimal.createCriteria().list(closure)*.name.sort() == ['Society Lady']

        where:
        shape                              | closure
        'an empty conjunction'             | { -> eq('name', 'Society Lady'); and {} }
        'an empty disjunction'             | { -> eq('name', 'Society Lady'); or {} }
        'an empty conjunction after a block' | { -> sire { eq('name', 'Mr. Prospector') }; eq('name', 'Society Lady'); and {} }
        'a conjunction of one criterion'   | { -> and { eq('name', 'Society Lady') } }
        'an empty conjunction in a conjunction' | { -> and { eq('name', 'Society Lady'); and {} } }
    }
}
