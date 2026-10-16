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
package grails.gorm.tests.generated

import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A GORM entity that reaches the datastore through {@code hibernate.annotatedClasses} is bound by the generated-domain-class
 * path like one passed to the constructor: Hibernate keeps the first class it is given for a name, so the real class must not
 * hide the class generated under the same name.
 */
class GeneratedDomainClassesAnnotatedClassesSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver([
                    'hibernate.annotatedClasses': [GdcAnnotatedThing],
                    'dataSource.url'            : 'jdbc:h2:mem:gdcAnnotatedClasses;LOCK_TIMEOUT=10000',
                    'dataSource.dbCreate'       : 'create-drop',
                    'hibernate.hbm2ddl.auto'    : 'create-drop',
            ]),
            GdcConstructorThing)

    void "an entity registered through annotatedClasses is bound under its own name and persists real instances"() {
        when:
        GdcAnnotatedThing saved = GdcAnnotatedThing.withNewTransaction {
            new GdcAnnotatedThing(name: 'a').save(flush: true)
        }
        GdcAnnotatedThing loaded = GdcAnnotatedThing.withNewSession { GdcAnnotatedThing.get(saved.id) }

        then:
        datastore.sessionFactory.mappingMetamodel.getEntityDescriptor(GdcAnnotatedThing).entityName == GdcAnnotatedThing.name
        loaded.class == GdcAnnotatedThing
        loaded.name == 'a'
    }

    void "an entity passed to the constructor is bound next to it"() {
        expect:
        GdcConstructorThing.withNewSession { GdcConstructorThing.count() } == 0
    }
}

@Entity
class GdcAnnotatedThing {

    String name
}

@Entity
class GdcConstructorThing {

    String name
}
