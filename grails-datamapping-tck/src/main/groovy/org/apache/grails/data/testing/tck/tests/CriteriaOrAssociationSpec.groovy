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

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * A criterion on an association inside {@code or { }} is one branch of the disjunction, so an entity that has no
 * such association is still found by the other branches. Every datastore has to give the same rows.
 */
class CriteriaOrAssociationSpec extends GrailsDataTckSpec {

    private ChildEntity createEntities() {
        ChildEntity kid = new ChildEntity(name: 'kid').save(flush: true)
        new TestEntity(name: 'with', child: kid).save(flush: true)
        new TestEntity(name: 'other', child: new ChildEntity(name: 'stranger')).save(flush: true)
        new TestEntity(name: 'orphan').save(flush: true)
        kid
    }

    private static List<String> names(List<TestEntity> found) {
        found*.name.sort()
    }

    void 'an association compared to an instance in or keeps the entities without the association'() {
        given:
        ChildEntity kid = createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                eq('child', kid)
                eq('name', 'orphan')
            }
        }

        then:
        names(found) == ['orphan', 'with']
    }

    void 'isNull on an association in or finds the entities without it and those another branch matches'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                isNull('child')
                eq('name', 'with')
            }
        }

        then:
        names(found) == ['orphan', 'with']
    }

    void 'isNotNull on an association in or keeps the entities without it that another branch matches'() {
        given:
        createEntities()

        when:
        def found = TestEntity.withCriteria {
            or {
                isNotNull('child')
                eq('name', 'orphan')
            }
        }

        then:
        names(found) == ['orphan', 'other', 'with']
    }
}
