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

import grails.gorm.tests.DomainOne
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Native domain binding is the only binding. An application upgraded from Grails 8 that still sets
 * {@code hibernate.generatedDomainClasses}, with either value, boots natively and is told once per data source, at startup,
 * that the setting is no longer used. The test JVM logs through slf4j-simple, which writes to {@code System.err}, so the
 * warning is read from there. The entity is an existing fixture, so the scanned entity set of the differential specs is
 * unchanged.
 */
class GeneratedDomainClassesSettingSpec extends Specification {

    private static final String WARNING = 'The setting [hibernate.generatedDomainClasses] is no longer used: ' +
            'native domain binding is the only binding of GORM for Hibernate 7'

    @AutoCleanup
    HibernateDatastore datastore

    private String bootAndCaptureLog(Map settings) {
        PrintStream original = System.err
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8.name()))
        try {
            datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                    'dataSource.url'                 : "jdbc:h2:mem:generatedSetting${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
                    'dataSource.dbCreate'            : 'create-drop',
                    'dataSource.dialect'             : H2Dialect.name,
                    'hibernate.hbm2ddl.auto'         : 'create-drop',
                    'hibernate.cache.queries'        : 'false',
                    'hibernate.cache.use_query_cache': 'false',
            ] + settings), DomainOne)
        } finally {
            System.setErr(original)
        }
        return captured.toString(StandardCharsets.UTF_8.name())
    }

    private static boolean persists() {
        return DomainOne.withTransaction {
            new DomainOne(controller: 'book', action: 'native').save(flush: true, failOnError: true)
            DomainOne.count() == 1
        }
    }

    void "a setting that selected the classic binding (#value) is ignored with one warning, and the application boots natively"() {
        when:
        String log = bootAndCaptureLog(['hibernate.generatedDomainClasses': value])

        then: 'the warning names the setting and says what to do, once'
        log.contains('WARN')
        log.count(WARNING) == 1
        log.contains('Remove the setting')

        and: 'the entity is bound under its own name and works'
        datastore.sessionFactory.mappingMetamodel.getEntityDescriptor(DomainOne).entityName == DomainOne.name
        persists()

        where:
        value << [false, true, 'false']
    }

    void "the setting of a named data source is reported with its own key"() {
        when:
        String log = bootAndCaptureLog([
                'dataSources.second.url'                            : "jdbc:h2:mem:generatedSettingSecond${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
                'dataSources.second.hibernate.generatedDomainClasses': false,
        ])

        then:
        log.contains('The setting [dataSources.second.hibernate.generatedDomainClasses] is no longer used')
        !log.contains(WARNING)
    }

    void "an application that does not set it starts without the warning"() {
        when:
        String log = bootAndCaptureLog([:])

        then:
        !log.contains('no longer used')
        persists()
    }
}
