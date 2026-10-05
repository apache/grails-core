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
import grails.gorm.tests.HibernateGormDatastoreSpec

/**
 * Proves, through the GORM API, that {@code insertable: false} and {@code updatable: false} in the mapping keep the
 * column out of the INSERT and the UPDATE statements. The database is read with a native query because the in-memory
 * entity still holds the value the application assigned.
 */
class InsertableUpdatableMappingSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(IUMColumns)
    }

    private String column(Long id, String name) {
        sessionFactory.currentSession
                .createNativeQuery("select ${name} from ium_columns where id = :id".toString(), String)
                .setParameter('id', id)
                .uniqueResult()
    }

    void "a column mapped insertable false is left to the database default on insert"() {
        given:
        IUMColumns row = new IUMColumns(writable: 'w', insertGuard: 'app', updateGuard: 'u').save(flush: true, failOnError: true)
        session.clear()

        expect:
        column(row.id, 'writable') == 'w'
        column(row.id, 'insert_guard') == 'db'
        column(row.id, 'update_guard') == 'u'
    }

    void "a column mapped updatable false keeps its inserted value on update"() {
        given:
        IUMColumns row = new IUMColumns(writable: 'w', insertGuard: 'app', updateGuard: 'u').save(flush: true, failOnError: true)
        session.clear()
        IUMColumns loaded = IUMColumns.get(row.id)
        loaded.writable = 'w2'
        loaded.updateGuard = 'u2'
        loaded.save(flush: true, failOnError: true)
        session.clear()

        expect:
        column(row.id, 'writable') == 'w2'
        column(row.id, 'update_guard') == 'u'
    }
}

@Entity
class IUMColumns {

    String writable
    String insertGuard
    String updateGuard

    static mapping = {
        table 'ium_columns'
        insertGuard insertable: false, defaultValue: "'db'"
        updateGuard updatable: false
    }
}
