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

import grails.gorm.annotation.Entity
import org.apache.grails.data.testing.tck.domains.Book
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.HibernateException
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.exception.GenericJDBCException
import org.hibernate.service.spi.ServiceRegistryImplementor
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.hibernate5.HibernateJdbcException
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Specification

class GrailsHibernateTemplateSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    GrailsHibernateTemplate template

    void setup() {
        datastore = new HibernateDatastore([(Settings.SETTING_DB_CREATE): 'create-drop'], Book, TemplateTestEntity)
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

    void "doExecute translates HibernateException thrown during flushIfNecessary to DataAccessException"() {
        given:
        template.setFlushMode(GrailsHibernateTemplate.FLUSH_EAGER)
        TemplateTestEntity entity = null
        TemplateTestEntity.withTransaction {
            entity = new TemplateTestEntity(code: 'abc').save(flush: true)
        }

        when: "an update exceeding column length is flushed during template execute"
        TemplateTestEntity.withTransaction {
            template.execute { Session session ->
                TemplateTestEntity loaded = session.get(TemplateTestEntity, entity.id)
                loaded.code = 'this string is much too long for varchar 5'
                null
            }
        }

        then:
        thrown(DataIntegrityViolationException)
    }

    void "executeWithNewSession restores outer session and propagates exception when openSession fails"() {
        given:
        Session outerSession = datastore.sessionFactory.openSession()
        SessionHolder outerHolder = new SessionHolder(outerSession)

        SessionFactoryImplementor mockFactory = Mock(SessionFactoryImplementor)
        ServiceRegistryImplementor registry = Mock(ServiceRegistryImplementor)
        mockFactory.getServiceRegistry() >> registry
        mockFactory.openSession() >> { throw new RuntimeException("Simulated openSession failure") }
        GrailsHibernateTemplate mockTemplate = new GrailsHibernateTemplate(mockFactory, datastore)
        TransactionSynchronizationManager.bindResource(mockFactory, outerHolder)

        when:
        mockTemplate.executeWithNewSession { Session s -> 'unreachable' }

        then:
        RuntimeException ex = thrown(RuntimeException)
        ex.message == "Simulated openSession failure"
        TransactionSynchronizationManager.getResource(mockFactory) == outerHolder

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(mockFactory)
        outerSession?.close()
    }
}

@Entity
class TemplateTestEntity {
    Long id
    Long version
    String code

    static mapping = {
        code length: 5
    }
}
