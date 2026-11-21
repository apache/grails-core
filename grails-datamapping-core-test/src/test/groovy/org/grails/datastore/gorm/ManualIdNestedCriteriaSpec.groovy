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

package org.grails.datastore.gorm

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.CriteriaGrandparent
import org.apache.grails.data.testing.tck.domains.CriteriaLeaf
import org.apache.grails.data.testing.tck.domains.CriteriaMiddle
import grails.persistence.Entity
import spock.lang.Issue

/**
 * A unit test that assigns the ids by hand, with a different id at each level, as reported against the in-memory
 * store: the nested query found nothing unless every object had the same id.
 */
@Issue('https://github.com/apache/grails-core/issues/14617')
class ManualIdNestedCriteriaSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.domainClasses.addAll([CriteriaGrandparent, CriteriaMiddle, CriteriaLeaf, AssignedTop, AssignedMid, AssignedEnd])
    }

    void "a block nested two deep finds the leaf when the ids were assigned by hand and differ per level"() {
        given:
        CriteriaGrandparent top = new CriteriaGrandparent(code: 'P1')
        top.id = 1
        top.save(flush: true, failOnError: true)
        CriteriaMiddle mid = new CriteriaMiddle(code: 'M1', parent: top)
        mid.id = 2
        mid.save(flush: true, failOnError: true)
        CriteriaLeaf end = new CriteriaLeaf(code: 'L1', child: mid)
        end.id = 3
        end.save(flush: true, failOnError: true)
        manager.session.clear()

        expect:
        CriteriaLeaf.createCriteria().list {
            child {
                parent {
                    eq('code', 'P1')
                }
            }
        }*.code == ['L1']
    }

    void "a block nested two deep finds the leaf when the entities declare assigned ids that differ per level"() {
        given:
        AssignedTop top = new AssignedTop(id: 1L, code: 'P1').save(flush: true, failOnError: true)
        AssignedMid mid = new AssignedMid(id: 2L, code: 'M1', parent: top).save(flush: true, failOnError: true)
        new AssignedEnd(id: 3L, code: 'L1', child: mid).save(flush: true, failOnError: true)
        manager.session.clear()

        expect:
        AssignedEnd.createCriteria().list {
            child {
                parent {
                    eq('code', 'P1')
                }
            }
        }*.code == ['L1']
    }
}

@Entity
class AssignedTop {
    Long id
    String code

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class AssignedMid {
    Long id
    String code
    AssignedTop parent

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class AssignedEnd {
    Long id
    String code
    AssignedMid child

    static mapping = {
        id generator: 'assigned'
    }
}
