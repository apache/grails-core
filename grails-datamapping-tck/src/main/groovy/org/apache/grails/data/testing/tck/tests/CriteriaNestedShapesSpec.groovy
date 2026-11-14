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
        manager.registerDomainClasses(CriteriaAnimal, CriteriaGrandparent, CriteriaMiddle, CriteriaLeaf)
    }

    /** two chains: p1 - m1 - l1 and p2 - m2 - l2, and a leaf l0 whose middle has no parent */
    private void seedChains() {
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
