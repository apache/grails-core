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

import jakarta.persistence.criteria.JoinType

import grails.gorm.DetachedCriteria

import grails.gorm.annotation.Entity

/**
 * A join type the caller names is kept: where the caller joined the association with a left join, a block on it in an
 * {@code or} finds the entities without the association, while a block that names none is joined with a left join by
 * the planner and requires the associated entity.
 */
class CriteriaOrAssociationJoinTypeHibernate7Spec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(OjParent, OjChild, OjCompositeParent, OjCompositeChild, OjTop, OjMid, OjLeaf)
    }

    private void createParents() {
        new OjParent(name: 'with', child: new OjChild(name: 'kid')).save(flush: true)
        new OjParent(name: 'other', child: new OjChild(name: 'stranger')).save(flush: true)
        new OjParent(name: 'orphan').save(flush: true)
    }

    void 'a block in or whose association the caller joined with a left join keeps the entities without the association'() {
        given:
        createParents()

        when:
        def found = OjParent.createCriteria().list {
            join('child', JoinType.LEFT)
            or {
                child { isNull('name') }
                eq('name', 'nobody')
            }
        }

        then:
        found*.name == ['orphan']
    }

    void 'a block that does not name a join type still requires the associated entity as a branch of or'() {
        given:
        createParents()

        when:
        def found = OjParent.withCriteria {
            or {
                child { isNull('name') }
                eq('name', 'nobody')
            }
        }

        then:
        found.isEmpty()
    }

    void 'a block in or that names an inner join keeps it through a criteria, so an entity without the association does not match'() {
        given:
        createParents()

        expect: 'through criteria'
        OjParent.createCriteria().list {
            or {
                child(0) { eq('name', 'kid') }
                eq('name', 'orphan')
            }
        }*.name == ['with']
    }

    void 'a block in or that names an inner join keeps it through a detached criteria'() {
        given:
        createParents()

        expect:
        new DetachedCriteria(OjParent).build {
            or {
                child(0) { eq('name', 'kid') }
                eq('name', 'orphan')
            }
        }.list()*.name == ['with']
    }

    void 'a block in or that names a left join finds the entity without the association'() {
        given:
        createParents()

        expect:
        OjParent.createCriteria().list {
            or {
                child(1) { isNull('name') }
                eq('name', 'nobody')
            }
        }*.name == ['orphan']
    }

    private void createTops() {
        new OjTop(name: 'full', mid: new OjMid(name: 'm1', leaf: new OjLeaf(name: 'leaf'))).save(flush: true)
        new OjTop(name: 'no-leaf', mid: new OjMid(name: 'm2')).save(flush: true)
        new OjTop(name: 'no-mid').save(flush: true)
    }

    void 'a later block that names a left join for a path an earlier block in an or left joined keeps the entities without the association'() {
        given:
        createTops()

        when:
        def found = OjTop.createCriteria().list {
            or {
                mid { leaf { isNull('name') } }
                eq('name', 'nobody')
            }
            mid { join('leaf', JoinType.LEFT) }
        }

        then: 'the join the caller named keeps the mid without a leaf, so its top matches the null test; the mid itself is still required'
        found*.name == ['no-leaf']
    }

    void 'a block in or on an association whose entity has a composite id still requires the associated entity'() {
        given:
        OjCompositeChild kid = new OjCompositeChild(area: 'a', code: 'b', name: null).save(flush: true)
        new OjCompositeParent(name: 'with', child: kid).save(flush: true)
        new OjCompositeParent(name: 'orphan').save(flush: true)

        when:
        def found = OjCompositeParent.createCriteria().list {
            or {
                child { isNull('name') }
                eq('name', 'nobody')
            }
        }

        then: 'the orphan has no child, so only the parent whose child has a null name matches'
        found*.name == ['with']
    }
}

@Entity
class OjCompositeChild implements Serializable {

    String area
    String code
    String name

    static mapping = {
        id composite: ['area', 'code']
        version false
    }

    @Override
    boolean equals(Object other) {
        other instanceof OjCompositeChild && other.area == area && other.code == code
    }

    @Override
    int hashCode() {
        Objects.hash(area, code)
    }

    static constraints = {
        name(nullable: true)
    }
}

@Entity
class OjCompositeParent {

    String name
    OjCompositeChild child

    static constraints = {
        child(nullable: true)
    }
}

@Entity
class OjParent {

    String name
    OjChild child

    static constraints = {
        child(nullable: true)
    }
}

@Entity
class OjChild {

    String name
}

@Entity
class OjTop {

    String name
    OjMid mid

    static constraints = {
        mid(nullable: true)
    }
}

@Entity
class OjMid {

    String name
    OjLeaf leaf

    static constraints = {
        leaf(nullable: true)
    }
}

@Entity
class OjLeaf {

    String name
}
