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

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Hibernate
import spock.lang.Specification

/**
 * A fetch that a mapping declares is joined by Hibernate only up to {@code hibernate.max_fetch_depth}
 * joins away from the queried class. A collection sort must preserve the complete bag whether the
 * mapping joins it or Hibernate loads it with another query.
 */
class MaxFetchDepthSortSpec extends Specification {

    private static HibernateDatastore createDatastore(Integer maxFetchDepth) {
        Map config = [
                'dataSource.url'               : "jdbc:h2:mem:maxFetchDepthSort${maxFetchDepth};LOCK_TIMEOUT=10000".toString(),
                'hibernate.hbm2ddl.auto'       : 'create',
                'hibernate.generate_statistics': 'true',
                // a sort by a collection is checked against the collections that the query fetches with this setting
                'grails.gorm.leftJoinAssociationSort': 'true',
        ]
        if (maxFetchDepth != null) {
            config['hibernate.max_fetch_depth'] = maxFetchDepth.toString()
        }
        final datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),
                AssociationSortEagerAuthor, AssociationSortEagerBook, AssociationSortMappedPublisher, AssociationSortMappedAward)
        AssociationSortEagerAuthor.withTransaction {
            final publisher = new AssociationSortMappedPublisher(name: 'Publisher').save(failOnError: true)
            ['W1', 'W2'].each { new AssociationSortMappedAward(title: it, publisher: publisher).save(failOnError: true) }
            final author = new AssociationSortEagerAuthor(name: 'Eager', publisher: publisher).save(failOnError: true)
            ['B1', 'B2'].each { new AssociationSortEagerBook(title: it, author: author).save(failOnError: true, flush: true) }
        }
        datastore
    }

    void 'with hibernate.max_fetch_depth #maxFetchDepth, a collection sort keeps the mapped bag complete'() {
        given:
        def datastore = createDatastore(maxFetchDepth)
        def statistics = datastore.sessionFactory.statistics

        when: 'the query without the sort shows whether Hibernate joins the bag, with one statement, or loads it with another'
        long statements = 0
        List<String> awards = null
        boolean initialized = false
        AssociationSortEagerAuthor.withNewSession {
            statistics.clear()
            final author = AssociationSortEagerAuthor.where { name ==~ '%' }.list().first()
            author.publisher.awards.size()
            statements = statistics.prepareStatementCount
        }
        AssociationSortEagerAuthor.withNewSession {
            final author = AssociationSortEagerAuthor.where { name ==~ '%' }.list(sort: 'books.title').first()
            initialized = Hibernate.isInitialized(author.publisher.awards)
            awards = author.publisher.awards*.title
        }

        then:
        (statements == 1) == bagJoined
        !bagJoined || initialized
        awards.sort() == ['W1', 'W2']

        cleanup:
        datastore.close()

        where:
        maxFetchDepth | bagJoined
        null          | true
        0             | false
        1             | false
        2             | true
    }
}
