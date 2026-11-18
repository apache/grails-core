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

import org.hibernate.mapping.Column
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The column {@code comment}, {@code defaultValue}, {@code read} and {@code write} of a mapping apply to an enum
 * column as they do to any other column.
 */
@Rollback
class EnumColumnExtrasSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(EceExtras, EcePlain, EceReadWrite)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Unroll
    void 'the comment, default value and read and write expressions of #property reach its column'() {
        given:
        var column = boundColumn(EceExtras, property)

        expect:
        column.comment == "the ${property}".toString()
        column.defaultValue == defaultValue
        column.customRead == "lower(${property})".toString()
        column.customWrite == 'upper(?)'

        where:
        property | defaultValue
        'label'  | "'X'"
        'status' | "'ON'"
    }

    void 'an enum property without a column config has no column extras'() {
        given:
        var column = boundColumn(EcePlain, 'status')

        expect:
        column.comment == null
        column.defaultValue == null
        column.customRead == null
        column.customWrite == null
    }

    void 'the default value of an enum column is in the schema'() {
        given:
        var columnDefault = datastore.sessionFactory.currentSession
                .createNativeQuery("select column_default from information_schema.columns where table_name = 'ECE_EXTRAS' and column_name = 'STATUS'")
                .uniqueResult()

        expect:
        columnDefault.toString().contains('ON')
    }

    void 'the read and write expressions of an enum column are used to store and load it'() {
        given:
        var row = new EceReadWrite(status: EceState.ON).save(flush: true, failOnError: true)
        datastore.sessionFactory.currentSession.clear()

        expect: 'the write expression stores the lower case name'
        datastore.sessionFactory.currentSession
                .createNativeQuery('select status from ece_read_write where id = :id')
                .setParameter('id', row.id)
                .uniqueResult() == 'on'

        and: 'the read expression turns it back into the enum'
        EceReadWrite.get(row.id).status == EceState.ON
    }

    private Column boundColumn(Class<?> domain, String property) {
        (Column) datastore.metadata.getEntityBinding(domain.name).getProperty(property).columnIterator.next()
    }
}

enum EceState {

    ON, OFF
}

@Entity
class EceExtras {

    String label
    EceState status

    static mapping = {
        label(comment: 'the label', defaultValue: "'X'", read: 'lower(label)', write: 'upper(?)')
        status(comment: 'the status', defaultValue: "'ON'", read: 'lower(status)', write: 'upper(?)')
    }
}

@Entity
class EcePlain {

    EceState status
}

@Entity
class EceReadWrite {

    EceState status

    static mapping = {
        status(read: 'upper(status)', write: 'lower(?)')
    }
}
