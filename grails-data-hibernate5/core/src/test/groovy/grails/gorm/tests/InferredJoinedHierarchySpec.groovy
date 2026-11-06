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
import spock.lang.Issue

/**
 * An abstract base class that says nothing about its strategy is joined when one of its subclasses sets
 * {@code tablePerHierarchy false}. The classes below it follow: a subclass that says nothing, and the subclasses
 * of that one, are joined as well, because the base class has no discriminator column to hold them.
 */
@Issue('https://github.com/apache/grails-core/pull/16845')
class InferredJoinedHierarchySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(InferredRoot, InferredJoinedChild, InferredPlainChild, InferredGrandChild)
    }

    void "a subclass of an unconfigured child follows the joined strategy inferred for the abstract base class"() {
        given:
        new InferredJoinedChild(joinedValue: 'j').save(flush: true)
        new InferredPlainChild(plainValue: 'p').save(flush: true)
        new InferredGrandChild(plainValue: 'p2', grandValue: 'g').save(flush: true)
        session.clear()

        expect: 'the session factory was built, and every level is stored and read back'
        InferredRoot.count() == 3
        InferredPlainChild.list()*.plainValue.sort() == ['p', 'p2']
        InferredGrandChild.list()*.grandValue == ['g']

        and: 'no level kept a discriminator column'
        def current = manager.hibernateDatastore.sessionFactory.currentSession
        (current.createNativeQuery("select count(*) from information_schema.columns where upper(table_name) = 'INFERRED_ROOT' and upper(column_name) = 'CLASS'").uniqueResult() as int) == 0
        (current.createNativeQuery("select count(*) from information_schema.tables where upper(table_name) = 'INFERRED_GRAND_CHILD'").uniqueResult() as int) == 1
    }
}

@Entity
abstract class InferredRoot {
    Long id
}

@Entity
class InferredJoinedChild extends InferredRoot {
    String joinedValue

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class InferredPlainChild extends InferredRoot {
    String plainValue
}

@Entity
class InferredGrandChild extends InferredPlainChild {
    String grandValue
}
