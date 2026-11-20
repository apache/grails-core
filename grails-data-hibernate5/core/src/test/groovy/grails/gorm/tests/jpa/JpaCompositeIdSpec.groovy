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

package grails.gorm.tests.jpa

import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

@Issue('https://github.com/apache/grails-core/issues/14494')
class JpaCompositeIdSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(JpaCompositeIdEntity, JpaSingleCodeIdEntity)

    @Rollback
    void 'a JPA entity with two @Id properties compiles and is saved by its composite id'() {
        when:
        new JpaCompositeIdEntity(orgId: 1L, userNo: 2L, name: 'org-user').save(flush: true, failOnError: true)
        JpaCompositeIdEntity.withSession { it.clear() }

        then:
        JpaCompositeIdEntity.count() == 1
        JpaCompositeIdEntity.get(new JpaCompositeIdEntity(orgId: 1L, userNo: 2L)).name == 'org-user'
    }

    @Unroll
    void 'the @Id properties on #layout #outcome'() {
        expect: 'the entity declares getId() only when exactly one @Id is declared anywhere in its hierarchy'
        (entity.declaredMethods.any { it.name == 'getId' }) == declaresGetId

        where:
        layout                                         | entity                  || declaresGetId
        'the entity (two)'                             | JpaCompositeIdEntity    || false
        'the entity (one)'                             | JpaSingleCodeIdEntity   || true
        'a mapped superclass and the entity (one each)' | JpaSplitIdEntity        || false
        'a mapped superclass only (the entity has none)' | JpaInheritedIdEntity    || false
        'a mapped superclass (two) and the entity (one)' | JpaTwoInheritedIdEntity || false
        'a plain superclass (one) and the entity (one)'  | JpaPlainIdChild         || true
        'a plain superclass (two) and the entity (one)'  | JpaPlainTwoIdChild      || true
        'an entity superclass (one) and the entity (one)' | JpaEntityBaseChild      || false
        'a mapped superclass (one) above a plain class, and the entity (one)' | JpaMappedAbovePlainChild || false
        'a plain superclass whose @Id is on a getter, and the entity (one)' | JpaPlainGetterIdChild || true
        'a mapped superclass and the entity declaring the same @Id (one)' | JpaRedeclaredIdEntity || true
        'a mapped superclass getter and the entity declaring the same @Id (one)' | JpaRedeclaredGetterIdEntity || true
        'the entity, on a getter (one)'                 | JpaGetterIdEntity       || true
        'the entity, on a field and on its getter (one)' | JpaFieldAndGetterIdEntity || true
        'a mapped superclass getter and the entity, with other names (two)' | JpaGetterSplitIdEntity || false
        'a mapped grandparent, a plain parent and the entity declaring the same @Id (one)' | JpaGrandRedeclaredIdEntity || true
        'an entity superclass and the entity declaring the same @Id (one)' | JpaEntityBaseRedeclared || true
        'two mapped superclasses and the entity (three)' | JpaThreeLevelIdEntity   || false
        outcome = declaresGetId ? 'give it a getId()' : 'leave getId() to the key'
    }

    @Rollback
    void 'a JPA entity with one @Id not named id still exposes it as getId'() {
        when:
        JpaSingleCodeIdEntity entity = new JpaSingleCodeIdEntity(code: 7L, name: 'seven').save(flush: true, failOnError: true)

        then:
        entity.id == 7L
    }
}

@Entity
class JpaCompositeIdEntity implements Serializable {
    @Id
    Long orgId
    @Id
    Long userNo
    String name
}

@Entity
class JpaSingleCodeIdEntity {
    @Id
    Long code
    String name
}

@MappedSuperclass
abstract class JpaOrgBase implements Serializable {
    @Id
    Long orgId
}

@Entity
class JpaSplitIdEntity extends JpaOrgBase {
    @Id
    String userNo
    String name
}

@Entity
class JpaInheritedIdEntity extends JpaOrgBase {
    String name
}

@MappedSuperclass
abstract class JpaTwoIdBase implements Serializable {
    @Id
    Long orgId
    @Id
    Long unitNo
}

@Entity
class JpaTwoInheritedIdEntity extends JpaTwoIdBase {
    @Id
    String userNo
}

abstract class JpaPlainIdBase {
    @Id
    Long orgId
}

@Entity
class JpaPlainIdChild extends JpaPlainIdBase {
    @Id
    String userNo
}

abstract class JpaPlainTwoIdBase {
    @Id
    Long orgId
    @Id
    Long unitNo
}

@Entity
class JpaPlainTwoIdChild extends JpaPlainTwoIdBase {
    @Id
    String userNo
}

@Entity
class JpaEntityBase implements Serializable {
    @Id
    Long orgId
}

@Entity
class JpaEntityBaseChild extends JpaEntityBase {
    @Id
    String userNo
}

class JpaPlainMiddle extends JpaOrgBase {
    String note
}

@Entity
class JpaMappedAbovePlainChild extends JpaPlainMiddle {
    @Id
    String userNo
}

abstract class JpaPlainGetterIdBase {
    @Id
    Long getOrgId() { null }
}

@Entity
class JpaPlainGetterIdChild extends JpaPlainGetterIdBase {
    @Id
    String userNo
}

@MappedSuperclass
abstract class JpaCodeBase implements Serializable {
    @Id
    Long code
}

@Entity
class JpaRedeclaredIdEntity extends JpaCodeBase {
    @Id
    Long code
    String name
}

@MappedSuperclass
abstract class JpaCodeGetterBase implements Serializable {
    private Long code

    @Id
    Long getCode() {
        code
    }

    void setCode(Long code) {
        this.code = code
    }
}

@Entity
class JpaRedeclaredGetterIdEntity extends JpaCodeGetterBase {
    @Id
    Long code
    String name
}

@Entity
class JpaGetterIdEntity {
    Long code

    @Id
    Long getCode() {
        code
    }
}

@Entity
class JpaFieldAndGetterIdEntity {
    @Id
    Long code

    @Id
    Long getCode() {
        code
    }
}

@MappedSuperclass
abstract class JpaGetterOrgBase implements Serializable {
    private Long orgId

    @Id
    Long getOrgId() {
        orgId
    }

    void setOrgId(Long orgId) {
        this.orgId = orgId
    }
}

@Entity
class JpaGetterSplitIdEntity extends JpaGetterOrgBase {
    @Id
    String userNo
}

class JpaPlainBetween extends JpaCodeBase {
    String note
}

@Entity
class JpaGrandRedeclaredIdEntity extends JpaPlainBetween {
    @Id
    Long code
}

@Entity
class JpaEntityBaseRedeclared extends JpaEntityBase {
    @Id
    Long orgId
}

@MappedSuperclass
abstract class JpaTopIdBase implements Serializable {
    @Id
    Long orgId
}

@MappedSuperclass
abstract class JpaMiddleIdBase extends JpaTopIdBase {
    @Id
    Long unitNo
}

@Entity
class JpaThreeLevelIdEntity extends JpaMiddleIdBase {
    @Id
    String userNo
}
