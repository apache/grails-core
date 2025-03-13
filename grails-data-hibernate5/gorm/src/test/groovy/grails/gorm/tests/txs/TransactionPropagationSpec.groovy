/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package grails.gorm.tests.txs

import grails.gorm.annotation.Entity
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.annotation.Propagation
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TransactionPropagationSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore = new HibernateDatastore(Book)

    @Issue('https://github.com/grails/grails-core/issues/10801')
    void "test transaction propagation settings"() {
        when:
        TransactionalService service = new TransactionalService()
        service.start()

        then:
        def e = thrown(RuntimeException)
        e.message == 'foo'
        service.count() == 1
        service.first().name == 'two'
    }

}

@Transactional
class TransactionalService {

    @ReadOnly
    int count() {
        Book.count
    }

    @ReadOnly
    Book first() {
        Book.first()
    }

    def start() {
        createBook()
        createAnotherBook()
        throw new RuntimeException('foo')
    }

    def createBook() {
        new Book(name: 'one').save(failOnError: true)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    def createAnotherBook() {
        new Book(name: 'two').save(failOnError: true)
    }

}
@Entity
class Book {

    String name

    static constraints = {
    }
}
