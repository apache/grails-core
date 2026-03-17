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
package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.hibernate.FetchMode
import org.hibernate.type.ForeignKeyDirection

class HibernateOneToOnePropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([OneToOneFace, OneToOneNose, OneToOneLeft, OneToOneRight])
    }

    void "getHibernateInverseSide returns HibernateOneToOneProperty"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        noseProp.getHibernateInverseSide() instanceof HibernateOneToOneProperty
        noseProp.getHibernateInverseSide().name == 'face'
    }

    void "isHibernateConstrained is false when other side does not have hasOne"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        !noseProp.isHibernateConstrained()
    }

    void "isHibernateConstrained is true when other side has hasOne"() {
        when:
        def noseEntity = mappingContext.getPersistentEntity(OneToOneNose.name)
        def faceProp = noseEntity.persistentProperties.find { it.name == 'face' } as HibernateOneToOneProperty

        then:
        faceProp.isHibernateConstrained()
    }

    void "getHibernateReferencedEntityName returns other side owner name when inverse exists"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        noseProp.getHibernateReferencedEntityName() == OneToOneNose.name
    }

    void "getHibernateReferencedPropertyName returns inverse side name when inverse exists"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        noseProp.getHibernateReferencedPropertyName() == 'face'
    }

    void "getHibernateReferencedPropertyName returns null when no inverse"() {
        when:
        def noseEntity = mappingContext.getPersistentEntity(OneToOneNose.name)
        // face belongs to OneToOneFace via hasOne — it has no inverse side from nose's perspective
        def faceProp = noseEntity.persistentProperties.find { it.name == 'face' } as HibernateOneToOneProperty

        then:
        // face's inverse is the nose prop on the owning side, so referencedPropertyName is 'nose'
        faceProp.getHibernateReferencedPropertyName() == 'nose'
    }

    void "getHibernateForeignKeyDirection returns TO_PARENT when not constrained"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        noseProp.getHibernateForeignKeyDirection() == ForeignKeyDirection.TO_PARENT
    }

    void "getHibernateForeignKeyDirection returns FROM_PARENT when constrained"() {
        when:
        def noseEntity = mappingContext.getPersistentEntity(OneToOneNose.name)
        def faceProp = noseEntity.persistentProperties.find { it.name == 'face' } as HibernateOneToOneProperty

        then:
        faceProp.getHibernateForeignKeyDirection() == ForeignKeyDirection.FROM_PARENT
    }

    void "getHibernateFetchMode returns DEFAULT when no fetch config"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        noseProp.getHibernateFetchMode() == FetchMode.DEFAULT
    }

    void "needsSimpleValueBinding is false when not constrained and inverse exists"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        !noseProp.needsSimpleValueBinding()
    }

    void "needsSimpleValueBinding is true when constrained"() {
        when:
        def noseEntity = mappingContext.getPersistentEntity(OneToOneNose.name)
        def faceProp = noseEntity.persistentProperties.find { it.name == 'face' } as HibernateOneToOneProperty

        then:
        faceProp.needsSimpleValueBinding()
    }

    void "isAssociationColumnNullable is false when bidirectional non-owning and inverse has hasOne"() {
        when:
        def noseEntity = mappingContext.getPersistentEntity(OneToOneNose.name)
        def faceProp = noseEntity.persistentProperties.find { it.name == 'face' } as HibernateOneToOneProperty

        then:
        !faceProp.isAssociationColumnNullable()
    }

    void "isAssociationColumnNullable is true when owning side declares hasOne"() {
        when:
        def faceEntity = mappingContext.getPersistentEntity(OneToOneFace.name)
        def noseProp = faceEntity.persistentProperties.find { it.name == 'nose' } as HibernateOneToOneProperty

        then:
        noseProp.isAssociationColumnNullable()
    }

    void "isAssociationColumnNullable is true when bidirectional non-owning but inverse does not have hasOne"() {
        when:
        def leftEntity = mappingContext.getPersistentEntity(OneToOneLeft.name)
        def rightProp = leftEntity.persistentProperties.find { it.name == 'right' } as HibernateOneToOneProperty

        then:
        rightProp.isAssociationColumnNullable()
    }
}

@Entity
class OneToOneFace implements HibernateEntity<OneToOneFace> {
    String name
    OneToOneNose nose
    static hasOne = [nose: OneToOneNose]
}

@Entity
class OneToOneNose implements HibernateEntity<OneToOneNose> {
    Boolean hasFreckles
    OneToOneFace face
    static belongsTo = [face: OneToOneFace]
}

@Entity
class OneToOneRight implements HibernateEntity<OneToOneRight> {
    String code
    OneToOneLeft left
}

@Entity
class OneToOneLeft implements HibernateEntity<OneToOneLeft> {
    String label
    OneToOneRight right
    static belongsTo = [right: OneToOneRight]
}
