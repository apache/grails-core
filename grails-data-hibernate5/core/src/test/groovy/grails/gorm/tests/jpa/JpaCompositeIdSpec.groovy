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

import jakarta.persistence.Entity
import jakarta.persistence.Id

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
