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
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDB103Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQL57Dialect
import org.hibernate.dialect.MySQL8Dialect
import spock.lang.Specification

import jakarta.persistence.criteria.JoinType

/**
 * updateAll and deleteAll apply a requested join type with a subquery. MySQL does not allow an update or a
 * delete to select from the table that it modifies, so a join that leads back to the queried class is
 * rejected there, instead of being left to fail in the database.
 */
class RequestedJoinBulkOperationDialectSpec extends Specification {

    private static HibernateDatastore createDatastore(Class dialect) {
        final mode = dialect == H2Dialect ? '' : ';MODE=MySQL'
        Map config = [
                'dataSource.url'        : "jdbc:h2:mem:requestedJoinBulk${dialect.simpleName}${mode};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dialect'    : dialect.name,
                'hibernate.hbm2ddl.auto': 'create',
        ]
        final datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), BulkJoinCategory, BulkJoinProduct)
        BulkJoinCategory.withTransaction {
            final root = new BulkJoinCategory(name: 'Root category').save(failOnError: true)
            final child = new BulkJoinCategory(name: 'Child category', parent: root).save(failOnError: true)
            new BulkJoinProduct(name: 'Categorized product', category: child).save(failOnError: true)
            new BulkJoinProduct(name: 'Top product', category: root).save(failOnError: true)
            new BulkJoinProduct(name: 'Uncategorized product').save(failOnError: true, flush: true)
        }
        datastore
    }

    void 'with #dialect.simpleName, #operation #outcome an inner join requested for an association to the queried class itself'() {
        given:
        final datastore = createDatastore(dialect)

        when:
        Number affected = null
        UnsupportedOperationException rejected = null
        final names = BulkJoinCategory.withTransaction {
            try {
                final query = BulkJoinCategory.where { name ==~ '%category' }.join('parent', JoinType.INNER)
                affected = operation == 'updateAll' ? query.updateAll(name: 'Updated') : query.deleteAll()
            }
            catch (UnsupportedOperationException e) {
                rejected = e
            }
            BulkJoinCategory.list()*.name.sort()
        }

        then:
        (rejected == null) == supported
        affected == (supported ? 1 : null)
        names == expected

        and: 'the message names the association'
        supported || rejected.message.contains('[parent]')

        cleanup:
        datastore?.close()

        where:
        dialect           | operation   | supported || expected
        H2Dialect         | 'updateAll' | true      || ['Root category', 'Updated']
        MariaDB103Dialect | 'updateAll' | true      || ['Root category', 'Updated']
        MySQL8Dialect     | 'updateAll' | false     || ['Child category', 'Root category']
        MySQL57Dialect    | 'updateAll' | false     || ['Child category', 'Root category']
        MariaDBDialect    | 'updateAll' | false     || ['Child category', 'Root category']
        MySQL8Dialect     | 'deleteAll' | false     || ['Child category', 'Root category']

        outcome = supported ? 'applies' : 'rejects'
    }

    void 'with #dialect.simpleName, updateAll applies an inner join requested for #description'() {
        given:
        final datastore = createDatastore(dialect)

        when:
        final names = BulkJoinProduct.withTransaction {
            BulkJoinProduct.where { name ==~ '%product' }.join(path, JoinType.INNER).updateAll(name: 'Updated')
            (BulkJoinProduct.list()*.name - 'Updated').sort()
        }

        then:
        names == unchanged

        cleanup:
        datastore?.close()

        where:
        dialect       | path              | description                                        || unchanged
        MySQL8Dialect | 'category'        | 'an association to another class'                  || ['Uncategorized product']
        MySQL8Dialect | 'category.parent' | 'a path that does not lead to the queried class'   || ['Top product', 'Uncategorized product']
    }

    void 'with #dialect.simpleName, criteria on the association restrict updateAll where the join is rejected'() {
        given:
        final datastore = createDatastore(dialect)

        when:
        final names = BulkJoinCategory.withTransaction {
            BulkJoinCategory.where { parent != null }.updateAll(name: 'Updated')
            BulkJoinCategory.list()*.name.sort()
        }

        then:
        names == ['Root category', 'Updated']

        cleanup:
        datastore?.close()

        where:
        dialect << [MySQL8Dialect]
    }
}

@Entity
class BulkJoinCategory {
    String name
    BulkJoinCategory parent

    static constraints = {
        parent nullable: true
    }
}

@Entity
class BulkJoinProduct {
    String name
    BulkJoinCategory category

    static constraints = {
        category nullable: true
    }
}
