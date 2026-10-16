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
package grails.gorm.tests.traits

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.util.ReflectionUtils
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.AccessType

/**
 * GORM-flow coverage for the runtime path that reaches
 * {@link org.grails.orm.hibernate.access.TraitPropertyAccessStrategy}: Spring clears its reflection
 * cache at the end of every context refresh, so a SessionFactory built afterwards (a connection
 * source added at runtime) sees an inaccessible generated trait field.
 */
class TraitPropertyRuntimeConnectionSourceSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'        : 'jdbc:h2:mem:traitFieldRuntime;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate'   : 'create-drop',
            'hibernate.hbm2ddl.auto': 'create-drop']), RuntimeTraitEntity)

    @Issue('https://github.com/apache/grails-core/issues/16615')
    void "a trait property is readable on a connection source added after Spring cleared its reflection cache"() {
        given: 'what Spring does at the end of every context refresh'
        ReflectionUtils.clearCache()

        when: 'a data source added at runtime builds a new SessionFactory'
        datastore.connectionSources.addConnectionSource('later', [pooled: true, dbCreate: 'create-drop',
                url: 'jdbc:h2:mem:traitFieldLater;LOCK_TIMEOUT=10000'])
        Long id = null
        RuntimeTraitEntity.later.withTransaction {
            def entity = new RuntimeTraitEntity(name: 'n', bar: 'b')
            entity.save(flush: true, failOnError: true)
            id = entity.id
        }
        String bar = null
        RuntimeTraitEntity.later.withNewSession { bar = RuntimeTraitEntity.later.get(id).bar }

        then:
        bar == 'b'
    }
}

trait RuntimeTrait {
    String bar
}

@Entity
class RuntimeTraitEntity implements RuntimeTrait {
    String name
    static mapping = {
        datasource 'ALL'
        bar accessType: AccessType.FIELD
    }
}
