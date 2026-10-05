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
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * What the generated-domain-class binding refuses to boot, and that it says so by entity name.
 */
class GeneratedDomainClassesBootSpec extends Specification {

    def "a composite identifier is rejected by name"() {
        when:
        new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([
                        'dataSource.url'                  : 'jdbc:h2:mem:gdcBoot;LOCK_TIMEOUT=10000',
                        'dataSource.dbCreate'             : 'create-drop',
                        'hibernate.generatedDomainClasses': true,
                ]), GdcComposite)

        then:
        RuntimeException e = thrown()
        causeMessages(e).any { String message -> message.contains('GdcComposite') && message.contains('composite identifier') }
    }

    def "the same entity boots through the domain binder"() {
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

    private static List<String> causeMessages(Throwable throwable) {
        List<String> messages = []
        for (Throwable current = throwable; current != null; current = current.cause) {
            messages << String.valueOf(current.message)
        }
        return messages
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
