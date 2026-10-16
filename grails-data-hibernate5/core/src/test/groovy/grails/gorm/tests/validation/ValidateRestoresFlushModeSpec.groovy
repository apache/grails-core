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
package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ValidateRestoresFlushModeSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(FlushModeBook)

    void setup() {
        FlushModeBook.withNewTransaction { FlushModeBook.executeUpdate('delete from FlushModeBook') }
    }

    void "a failed validate() restores the session flush mode so the transaction still flushes valid saves on commit"() {
        given:
        FlushMode modeBefore = null
        FlushMode modeAfter = null
        boolean valid = true

        when:
        FlushModeBook.withTransaction {
            def session = hibernateDatastore.sessionFactory.currentSession
            new FlushModeBook(title: 'valid one').save()
            def invalid = new FlushModeBook(title: null)
            modeBefore = session.hibernateFlushMode
            valid = invalid.validate()
            modeAfter = session.hibernateFlushMode
            new FlushModeBook(title: 'valid two').save()
        }

        then:
        !valid
        modeAfter == modeBefore
        modeAfter != FlushMode.MANUAL
        FlushModeBook.withNewSession { FlushModeBook.list()*.title.sort() } == ['valid one', 'valid two']
    }

    void "an invalid instance that failed validate() is not persisted by the commit flush"() {
        when:
        FlushModeBook.withTransaction {
            new FlushModeBook(title: 'ok').save()
            assert !new FlushModeBook(title: null).validate()
            assert new FlushModeBook(title: null).save() == null
        }

        then:
        FlushModeBook.withNewSession { FlushModeBook.list()*.title } == ['ok']
    }

    void "clearErrors then validate again within a transaction still restores the flush mode"() {
        given:
        FlushMode mode = null

        when:
        FlushModeBook.withTransaction {
            def session = hibernateDatastore.sessionFactory.currentSession
            FlushMode before = session.hibernateFlushMode
            def book = new FlushModeBook(title: null)
            assert !book.validate()
            book.clearErrors()
            assert !book.validate()
            mode = session.hibernateFlushMode
            assert mode == before
            new FlushModeBook(title: 'saved').save()
        }

        then:
        mode != FlushMode.MANUAL
        FlushModeBook.withNewSession { FlushModeBook.list()*.title } == ['saved']
    }

    void "validate in an earlier transaction does not stop a later transaction from flushing"() {
        given:
        def invalid = new FlushModeBook(title: null)

        when:
        boolean valid = FlushModeBook.withNewTransaction { invalid.validate() }
        FlushModeBook.withTransaction {
            new FlushModeBook(title: 'after').save()
        }

        then:
        !valid
        FlushModeBook.withNewSession { FlushModeBook.list()*.title } == ['after']
    }
}

@Entity
class FlushModeBook {
    String title

    static constraints = {
        title nullable: false
    }
}
