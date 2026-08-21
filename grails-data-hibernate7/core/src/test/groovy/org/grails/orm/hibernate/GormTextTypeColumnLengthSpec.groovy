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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.Length
import org.hibernate.mapping.PersistentClass

/**
 * Covers https://github.com/apache/grails-core/issues/16010 against the default H2 datastore
 * used by the rest of the test suite, so the {@code type: 'text'} column-length behaviour is
 * exercised even when Docker (and so {@link GormTextTypeColumnIntegrationSpec}'s Postgres/MySQL/
 * MariaDB Testcontainers) is unavailable.
 */
class GormTextTypeColumnLengthSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(UnboundedTextTypeMessage, BoundedTextTypeMessage)
    }

    void "a property mapped with type 'text' and no explicit length is bound to Length.LONG32"() {
        when:
        PersistentClass persistentClass = datastore.getMetadata().getEntityBinding(UnboundedTextTypeMessage.name)
        def column = persistentClass.getProperty('body').getColumns().first()

        then:
        column.getLength() == Length.LONG32 as Long
    }

    void "a property mapped with type 'text' and an explicit maxSize keeps the bounded length"() {
        when:
        PersistentClass persistentClass = datastore.getMetadata().getEntityBinding(BoundedTextTypeMessage.name)
        def column = persistentClass.getProperty('body').getColumns().first()

        then:
        column.getLength() == 500L
    }

    void "a property mapped with type 'text' and no explicit length produces an unbounded H2 CLOB column"() {
        when:
        Map<String, Object> column
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery('''
                    select data_type, character_maximum_length
                    from information_schema.columns
                    where table_name = 'UNBOUNDED_TEXT_TYPE_MESSAGE' and column_name = 'BODY'
                '''.stripIndent()).with { rs ->
                    rs.next()
                    column = [dataType: rs.getString('data_type'), maxLength: rs.getObject('character_maximum_length')]
                }
            }
        }

        then:
        column.dataType == 'CHARACTER LARGE OBJECT'
        column.maxLength == Long.MAX_VALUE
    }
}

@Entity
class UnboundedTextTypeMessage implements HibernateEntity<UnboundedTextTypeMessage> {
    String body

    static mapping = {
        body type: 'text'
    }
}

@Entity
class BoundedTextTypeMessage implements HibernateEntity<BoundedTextTypeMessage> {
    String body

    static constraints = {
        body maxSize: 500
    }

    static mapping = {
        body type: 'text'
    }
}
