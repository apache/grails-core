package org.grails.datastore.gorm.neo4j

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Person
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import spock.lang.Ignore

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class TransactionPropagationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Person]
    }

    void "Test nested setRollbackOnly() transaction"() {
        when:"An entity is persisted in a nested transaction"
        Person.withTransaction {
            new Person(lastName:"person1").save()
            Person.withTransaction { TransactionStatus status ->
                new Person(lastName:"person2").save()
                status.setRollbackOnly()
            }

        }
        session.disconnect()

        then:"Both transactions are rolled back"
        Person.count() == 0

    }

    @Ignore // Neo4j
    void "Test nested REQUIRES_NEW transaction"() {
        when:"An entity is persisted in a nested transaction"
        Person.withTransaction {
            new Person(lastName:"person1").save()
            Person.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW) { TransactionStatus status ->
                new Person(lastName:"person2").save()
            }
            throw new RuntimeException("bad")
        }

        then:"Both transactions are rolled back"
        thrown RuntimeException
        Person.count() == 1
        Person.findByLastName('person2')

    }

    void "Test nested exception transaction"() {
        when:"An entity is persisted in a nested transaction"
        try {
            Person.withTransaction {
                new Person(lastName:"person1").save()
                Person.withTransaction { TransactionStatus status ->
                    new Person(lastName:"person2").save()
                    throw new RuntimeException("bad")
                }

            }
        } finally {
            session.disconnect()
        }

        then:"Both transactions are rolled back"
        thrown RuntimeException
        Person.count() == 0

    }
}
