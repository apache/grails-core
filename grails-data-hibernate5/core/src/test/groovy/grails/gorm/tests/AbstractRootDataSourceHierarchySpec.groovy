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

@Issue('https://github.com/apache/grails-core/issues/14423')
class AbstractRootDataSourceHierarchySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.grailsConfig.dataSources.archive = [url: 'jdbc:h2:mem:abstractRootArchive;LOCK_TIMEOUT=10000']
        manager.registerDomainClasses(SharedRoot, DefaultOnlyChild, ArchiveOnlyChild)
    }

    void "a subclass mapped to another datasource does not change the hierarchy of the default datasource"() {
        when: 'the default datasource holds an abstract root and a child that keeps the default strategy'
        DefaultOnlyChild.withTransaction {
            new DefaultOnlyChild(label: 'kept').save(flush: true)
        }
        def sql = manager.hibernateDatastore.sessionFactory.currentSession
        int childTables = sql.createNativeQuery("select count(*) from information_schema.tables where upper(table_name) = 'DEFAULT_ONLY_CHILD'").uniqueResult() as int
        int discriminators = sql.createNativeQuery("select count(*) from information_schema.columns where upper(table_name) = 'SHARED_ROOT' and upper(column_name) = 'CLASS'").uniqueResult() as int

        then: 'the child is stored in the root table with a discriminator, not in a table of its own'
        childTables == 0
        discriminators == 1
        DefaultOnlyChild.list()*.label == ['kept']
    }
}

@Entity
abstract class SharedRoot {
    Long id
    String label

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class DefaultOnlyChild extends SharedRoot {
}

@Entity
class ArchiveOnlyChild extends SharedRoot {
    static mapping = {
        datasource 'archive'
        tablePerHierarchy false
    }
}
