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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification


/**
 * Created by graemerocher on 20/10/16.
 */
class SequenceIdSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(BookWithSequence)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    void "test sequence generator"() {
        when:"A book is saved"
        BookWithSequence book = new BookWithSequence(title: 'The Stand')
        book.save(flush:true)

        then:"The entity was saved"
        BookWithSequence.first()

        ((SessionImplementor)datastore.sessionFactory.currentSession).connection().prepareStatement("call NEXT VALUE FOR book_seq;")
                .executeQuery()
                .next()
    }
}
@Entity
class BookWithSequence {
    String title

    static constraints = {
    }

    static mapping = {
        version false
        id generator:'sequence', params:[sequence:'book_seq']
        id index:'book_id_idx'
    }
}