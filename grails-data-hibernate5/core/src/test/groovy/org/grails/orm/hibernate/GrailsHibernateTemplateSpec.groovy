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

import java.sql.SQLException

import org.apache.grails.data.testing.tck.domains.Book
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.hibernate.exception.GenericJDBCException
import org.springframework.dao.DataAccessException
import org.springframework.orm.hibernate5.HibernateJdbcException
import spock.lang.AutoCleanup
import spock.lang.Specification

class GrailsHibernateTemplateSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    GrailsHibernateTemplate template

    void setup() {
        datastore = new HibernateDatastore([(Settings.SETTING_DB_CREATE): 'create-drop'], Book)
        template = new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
    }

    void "convertHibernateAccessException falls back to SessionFactoryUtils when translator returns null"() {
        given:
        GenericJDBCException genericEx = new GenericJDBCException('msg', new SQLException('untranslatable'))

        when:
        DataAccessException converted = template.convertHibernateAccessException(genericEx)

        then:
        converted instanceof HibernateJdbcException
        converted.cause == genericEx
    }

    void "doExecute wraps untranslatable GenericJDBCException as HibernateJdbcException"() {
        when:
        Book.withTransaction {
            template.execute { Session sess -> throw new GenericJDBCException('msg', new SQLException('untranslatable')) }
        }

        then:
        thrown(HibernateJdbcException)
    }

    void "executeWithNewSession translates raw HibernateException to Spring DataAccessException"() {
        when:
        Book.withNewSession { Session s ->
            s.get(Book, 'not-a-long')
        }

        then:
        thrown(org.springframework.orm.hibernate5.HibernateSystemException)
    }
}
