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
 * Native domain binding is the default. An application that still selects the classic domain binder with
 * {@code hibernate.generatedDomainClasses: false} boots, and is told once per data source, at startup, that the classic
 * binding is deprecated and will be removed. The test JVM logs through slf4j-simple, which writes to {@code System.err},
 * so the warning is read from there. The entity is an existing fixture, so the scanned entity set of the differential
 * specs is unchanged.
 */
class ClassicDomainBindingDeprecationSpec extends Specification {

    private static final String WARNING = 'Classic domain binding is deprecated and will be removed: ' +
            'hibernate.generatedDomainClasses is false for data source [default]'

    @AutoCleanup
    HibernateDatastore datastore

    private String bootAndCaptureLog(Map settings) {
        PrintStream original = System.err
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8.name()))
        try {
            datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                    'dataSource.url'                 : "jdbc:h2:mem:classicDeprecation${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
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

    void "selecting the classic domain binder logs that it is deprecated and will be removed"() {
        when:
        String log = bootAndCaptureLog(['hibernate.generatedDomainClasses': false])

        then: 'the warning names the setting and the data source, and the application still works'
        log.contains('WARN')
        log.contains(WARNING)
        log.contains('remove the setting, or set it to true')
        DomainOne.withTransaction {
            new DomainOne(controller: 'book', action: 'classic').save(flush: true, failOnError: true)
            DomainOne.count() == 1
        }
    }

    void "the default, native domain binding, starts without the warning"() {
        given: 'the suite may run through the classic binder as a whole, which makes the classic binder the default'
        boolean classicSuite = Boolean.getBoolean('grails.hibernate.classicDomainBinding')

        when:
        String log = bootAndCaptureLog([:])

        then:
        log.contains(WARNING) == classicSuite
        DomainOne.withTransaction {
            new DomainOne(controller: 'book', action: 'native').save(flush: true, failOnError: true)
            DomainOne.count() == 1
        }
    }

    void "selecting native domain binding explicitly starts without the warning"() {
        when:
        String log = bootAndCaptureLog(['hibernate.generatedDomainClasses': true])

        then:
        !log.contains(WARNING)
    }
}
