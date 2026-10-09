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
package grails.gorm.tests.generated

import grails.persistence.Entity
import org.hibernate.persister.entity.EntityPersister
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * What boots through the generated classes (an entity with a composite identifier), with the setting stated and with the defaults.
 */
class GeneratedDomainClassesBootSpec extends Specification {

    def "a composite identifier boots through the generated classes, and the entity has no identifier property of its own"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([
                        'dataSource.url'                  : 'jdbc:h2:mem:gdcBoot;LOCK_TIMEOUT=10000',
                        'dataSource.dbCreate'             : 'create-drop',
                        'hibernate.generatedDomainClasses': true,
                ]), GdcComposite)
        EntityPersister persister = datastore.sessionFactory.mappingMetamodel.getEntityDescriptor(GdcComposite)

        then:
        persister.entityName == GdcComposite.name
        persister.mappedClass == GdcComposite
        persister.identifierPropertyName == null
        persister.identifierMapping.virtualIdEmbeddable.mappedJavaType.javaTypeClass == GdcComposite

        cleanup:
        datastore?.close()
    }

    def "the same entity boots with the default settings"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([
                        'dataSource.url'     : 'jdbc:h2:mem:gdcBootDefault;LOCK_TIMEOUT=10000',
                        'dataSource.dbCreate': 'create-drop',
                ]), GdcComposite)

        then:
        datastore.sessionFactory.mappingMetamodel.getEntityDescriptor(GdcComposite).entityName == GdcComposite.name

        cleanup:
        datastore?.close()
    }
}

@Entity
class GdcComposite implements Serializable {
    String first
    String second
    static mapping = {
        id composite: ['first', 'second']
    }
}
