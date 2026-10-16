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
package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A GORM entity can reach the datastore two ways: as a constructor argument, or through the
 * {@code hibernate.annotatedClasses} setting, which only registers it while the session factory is being built.
 * Both must end up bound to the datastore's data source and mapped to a table.
 */
class AdditionalClassBindingSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver([
                    'hibernate.annotatedClasses': [AdditionalBoundThing],
                    'dataSource.url'            : 'jdbc:h2:mem:additionalClassBinding;LOCK_TIMEOUT=10000',
                    'dataSource.dbCreate'       : 'create-drop',
                    'hibernate.hbm2ddl.auto'    : 'create-drop',
            ]),
            PrimaryBoundThing)

    void "an entity registered through annotatedClasses is bound to the datastore's data source"() {
        when:
        GrailsHibernatePersistentEntity entity =
                (GrailsHibernatePersistentEntity) datastore.mappingContext.getPersistentEntity(AdditionalBoundThing.name)

        then:
        entity != null
        entity.dataSourceName == ConnectionSource.DEFAULT
    }

    void "an entity registered through annotatedClasses is mapped to a table"() {
        expect:
        AdditionalBoundThing.withNewSession { AdditionalBoundThing.count() } == 0
    }

    void "an entity passed to the constructor is bound to the datastore's data source"() {
        when:
        GrailsHibernatePersistentEntity entity =
                (GrailsHibernatePersistentEntity) datastore.mappingContext.getPersistentEntity(PrimaryBoundThing.name)

        then:
        entity.dataSourceName == ConnectionSource.DEFAULT
        PrimaryBoundThing.withNewSession { PrimaryBoundThing.count() } == 0
    }
}

@Entity
class PrimaryBoundThing {

    String name
}

@Entity
class AdditionalBoundThing {

    String name
}
