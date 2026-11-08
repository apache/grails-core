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
 * An abstract base class that declares its strategy keeps it when a subclass declares another one. Only a base class
 * that says nothing follows a subclass that sets {@code tablePerHierarchy false}.
 */
@Issue('https://github.com/apache/grails-core/pull/16845')
class DeclaredRootStrategySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(DeclaredRootTph, DeclaredRootTphChild, DeclaredRootSingle, DeclaredRootSingleChild,
                SilentRoot, SilentRootChild)
    }

    private int count(String sql) {
        def session = manager.hibernateDatastore.sessionFactory.openSession()
        try {
            return session.createNativeQuery(sql).uniqueResult() as int
        }
        finally {
            session.close()
        }
    }

    private boolean hasDiscriminator(String table) {
        count("select count(*) from information_schema.columns where upper(table_name) = '${table}' and upper(column_name) = 'CLASS'") == 1
    }

    void "a base class that declares tablePerHierarchy true keeps its discriminator when a subclass is joined"() {
        expect:
        hasDiscriminator('DECLARED_ROOT_TPH')
    }

    void "a base class that declares tablePerSubclass false keeps its discriminator when a subclass is joined"() {
        expect:
        hasDiscriminator('DECLARED_ROOT_SINGLE')
    }

    void "a base class that says nothing is joined with a subclass that is"() {
        expect:
        !hasDiscriminator('SILENT_ROOT')
    }

    void "the rows of the base table of a declared base class are still returned"() {
        given:
        new DeclaredRootTphChild(note: 'child').save(flush: true, failOnError: true)
        session.clear()

        expect:
        DeclaredRootTph.count() == 1
        DeclaredRootTph.list()*.class == [DeclaredRootTphChild]
    }
}

@Entity
abstract class DeclaredRootTph {
    Long id

    static mapping = {
        tablePerHierarchy true
    }
}

@Entity
class DeclaredRootTphChild extends DeclaredRootTph {
    String note

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
abstract class DeclaredRootSingle {
    Long id

    static mapping = {
        tablePerSubclass false
    }
}

@Entity
class DeclaredRootSingleChild extends DeclaredRootSingle {
    String note

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
abstract class SilentRoot {
    Long id
}

@Entity
class SilentRootChild extends SilentRoot {
    String note

    static mapping = {
        tablePerHierarchy false
    }
}
