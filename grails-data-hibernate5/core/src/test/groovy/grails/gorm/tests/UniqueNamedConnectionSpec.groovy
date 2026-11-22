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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * A save that names a connection, such as {@code book.second.save()}, checks the {@code unique} constraint on that
 * connection and not on the default one.
 */
@Issue('https://github.com/apache/grails-core/issues/16934')
class UniqueNamedConnectionSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'      : 'jdbc:h2:mem:uncDefault;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate' : 'create-drop',
            'dataSource.dialect'  : H2Dialect.name,
            'dataSources.second'  : [url: 'jdbc:h2:mem:uncSecond;LOCK_TIMEOUT=10000'],
            'dataSources.third'   : [url: 'jdbc:h2:mem:uncThird;LOCK_TIMEOUT=10000']
    ]), UncBook)

    void setup() {
        UncBook.withNewTransaction { new UncBook(name: 'onDefault').save(validate: false, flush: true) }
        UncBook.second.withNewTransaction { new UncBook(name: 'onSecond').second.save(validate: false, flush: true) }
    }

    void cleanup() {
        UncBook.withNewTransaction { UncBook.executeUpdate('delete from UncBook') }
        UncBook.second.withNewTransaction { UncBook.second.executeUpdate('delete from UncBook') }
        UncBook.third.withNewTransaction { UncBook.third.executeUpdate('delete from UncBook') }
    }

    private <T> T withSessions(Closure<T> work) {
        UncBook.withNewSession {
            UncBook.second.withNewSession {
                UncBook.third.withNewSession(work)
            }
        }
    }

    void 'a save that names the second connection checks the second connection'() {
        when:
        UncBook onSecond = new UncBook(name: 'onSecond')
        UncBook onDefault = new UncBook(name: 'onDefault')
        List saved = withSessions { [onSecond.second.save(), onDefault.second.save()] }

        then: 'the value of the second connection is refused and the one of the default connection is saved'
        saved[0] == null
        onSecond.hasErrors()
        saved[1] != null
        !onDefault.hasErrors()
    }

    void 'the API of the second connection checks the second connection for save and insert'() {
        when:
        UncBook savedOnSecond = new UncBook(name: 'onSecond')
        UncBook insertedOnSecond = new UncBook(name: 'onSecond')
        UncBook savedOnDefault = new UncBook(name: 'onDefault')
        List results = withSessions {
            [UncBook.second.save(savedOnSecond), UncBook.second.insert(insertedOnSecond), UncBook.second.save(savedOnDefault)]
        }

        then:
        results[0] == null
        results[1] == null
        results[2] != null
    }

    void 'a save that names the third connection inside a block of the second checks the third'() {
        when:
        UncBook book = new UncBook(name: 'onSecond')
        UncBook saved = UncBook.withNewSession {
            UncBook.second.withNewTransaction {
                UncBook.third.withNewTransaction { book.third.save() }
            }
        }

        then: 'the third has no such value'
        book.errors.allErrors.toString() == '[]'
        saved != null
    }

    void 'a save through the default connection still checks the default connection'() {
        when:
        UncBook onDefault = new UncBook(name: 'onDefault')
        UncBook onSecond = new UncBook(name: 'onSecond')
        List saved = withSessions { [onDefault.save(), onSecond.save()] }

        then:
        saved[0] == null
        saved[1] != null
    }
}

@Entity
class UncBook {

    String name

    static constraints = {
        name(unique: true)
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}
