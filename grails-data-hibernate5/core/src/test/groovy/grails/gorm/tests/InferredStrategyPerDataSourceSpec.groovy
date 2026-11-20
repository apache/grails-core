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
 * The strategy inferred for an abstract base class belongs to the datasource being built. A base class mapped to
 * every datasource is joined on the default datasource when a subclass that lives there sets
 * {@code tablePerHierarchy false}, and it stays single-table on the archive datasource, where its own subclass
 * says nothing.
 */
@Issue('https://github.com/apache/grails-core/pull/16845')
class InferredStrategyPerDataSourceSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.grailsConfig.dataSources.archive = [url: 'jdbc:h2:mem:inferredStrategyArchive;LOCK_TIMEOUT=10000']
        manager.registerDomainClasses(PerDsRoot, PerDsDefaultJoinedChild, PerDsArchiveChild)
    }

    private int count(def sessionFactory, String sql) {
        def session = sessionFactory.openSession()
        try {
            return session.createNativeQuery(sql).uniqueResult() as int
        }
        finally {
            session.close()
        }
    }

    void "a base class joined on the default datasource stays single-table on the archive datasource"() {
        given:
        def archive = manager.hibernateDatastore.getDatastoreForConnection('archive').sessionFactory

        expect: 'the archive child is in the base table with a discriminator, not in a table of its own'
        count(archive, "select count(*) from information_schema.tables where upper(table_name) = 'PER_DS_ARCHIVE_CHILD'") == 0
        count(archive, "select count(*) from information_schema.columns where upper(table_name) = 'PER_DS_ROOT' and upper(column_name) = 'CLASS'") == 1
    }

    void "the default datasource still joins the subclass that asks for it"() {
        given:
        def defaults = manager.hibernateDatastore.sessionFactory

        expect:
        count(defaults, "select count(*) from information_schema.tables where upper(table_name) = 'PER_DS_DEFAULT_JOINED_CHILD'") == 1
        count(defaults, "select count(*) from information_schema.columns where upper(table_name) = 'PER_DS_ROOT' and upper(column_name) = 'CLASS'") == 0
    }
}

@Entity
abstract class PerDsRoot {
    Long id

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class PerDsDefaultJoinedChild extends PerDsRoot {
    static mapping = {
        // the test datastore names its default datasource 'default'
        datasource 'default'
        tablePerHierarchy false
    }
}

@Entity
class PerDsArchiveChild extends PerDsRoot {
    String note

    static mapping = {
        datasource 'archive'
    }
}
