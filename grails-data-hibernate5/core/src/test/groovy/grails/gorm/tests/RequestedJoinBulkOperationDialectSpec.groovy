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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDB103Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQL57Dialect
import org.hibernate.dialect.MySQL8Dialect
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.Specification

import jakarta.persistence.criteria.JoinType

/**
 * updateAll and deleteAll apply a requested join type with a subquery. MySQL does not allow an update or a
 * delete to select from the table that it modifies, so for a join that leads back to the queried class the
 * identifiers of the instances are selected first there, and the instances are updated or deleted by them.
 */
class RequestedJoinBulkOperationDialectSpec extends Specification {

    private final SqlCapture sqlCapture = new SqlCapture()

    private HibernateDatastore createDatastore(Class dialect) {
        final mode = dialect == H2Dialect ? '' : ';MODE=MySQL'
        Map config = [
                'dataSource.url'                               : "jdbc:h2:mem:requestedJoinBulk${dialect.simpleName}${mode};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dialect'                           : dialect.name,
                'hibernate.hbm2ddl.auto'                       : 'create',
                'hibernate.session_factory.statement_inspector': sqlCapture,
        ]
        final datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), BulkJoinCategory, BulkJoinProduct, BulkJoinPost)
        BulkJoinCategory.withTransaction {
            final root = new BulkJoinCategory(name: 'Root category').save(failOnError: true)
            final child = new BulkJoinCategory(name: 'Child category', parent: root).save(failOnError: true)
            new BulkJoinProduct(name: 'Categorized product', category: child).save(failOnError: true)
            new BulkJoinProduct(name: 'Top product', category: root).save(failOnError: true)
            new BulkJoinProduct(name: 'Uncategorized product').save(failOnError: true, flush: true)
        }
        datastore
    }

    /**
     * The updates and deletes of a table that select from a table in a subquery.
     */
    private List<String> modificationsWithASubquery(String table) {
        sqlCapture.statements*.toLowerCase().findAll {
            (it.startsWith("update ${table} ") || it.startsWith("delete from ${table} ")) && it.contains('select')
        }
    }

    void 'with #dialect.simpleName, #operation applies an inner join requested for an association to the queried class itself'() {
        given: 'no product refers to a category'
        final datastore = createDatastore(dialect)
        BulkJoinProduct.withTransaction {
            BulkJoinProduct.list()*.delete(flush: true)
        }
        sqlCapture.clear()

        when:
        DetachedCriteria<BulkJoinCategory> query = BulkJoinCategory.where { name ==~ '%category' }.join('parent', JoinType.INNER)
        final affected = BulkJoinCategory.withTransaction {
            operation == 'updateAll' ? query.updateAll(name: 'Updated') : query.deleteAll()
        }
        final names = BulkJoinCategory.withTransaction {
            BulkJoinCategory.list()*.name.sort()
        }

        then:
        affected == 1
        names == expected

        and: 'a dialect of a database that cannot select from the table that it modifies does not'
        (modificationsWithASubquery('bulk_join_category') != []) == selectsFromModifiedTable

        cleanup:
        datastore?.close()

        where:
        dialect           | operation   | selectsFromModifiedTable || expected
        H2Dialect         | 'updateAll' | true                     || ['Root category', 'Updated']
        MariaDB103Dialect | 'updateAll' | true                     || ['Root category', 'Updated']
        MySQL8Dialect     | 'updateAll' | false                    || ['Root category', 'Updated']
        MySQL57Dialect    | 'updateAll' | false                    || ['Root category', 'Updated']
        MariaDBDialect    | 'updateAll' | false                    || ['Root category', 'Updated']
        H2Dialect         | 'deleteAll' | true                     || ['Root category']
        MySQL8Dialect     | 'deleteAll' | false                    || ['Root category']
    }

    void 'with #dialect.simpleName, updateAll applies a right join requested for an association to the queried class itself'() {
        given:
        final datastore = createDatastore(dialect)

        when: 'the right join also has a row without a category for the category that is the parent of none'
        final affected = BulkJoinCategory.withTransaction {
            new DetachedCriteria(BulkJoinCategory).join('parent', JoinType.RIGHT).updateAll(name: 'Updated')
        }
        final names = BulkJoinCategory.withTransaction {
            BulkJoinCategory.list()*.name.sort()
        }

        then:
        affected == 1
        names == ['Root category', 'Updated']

        cleanup:
        datastore?.close()

        where:
        dialect << [H2Dialect, MySQL8Dialect]
    }

    void 'with #dialect.simpleName, updateAll applies an inner join requested for a collection of a basic type'() {
        given:
        final datastore = createDatastore(dialect)
        BulkJoinPost.withTransaction {
            new BulkJoinPost(title: 'Tagged post', tags: ['x', 'y']).save(failOnError: true)
            new BulkJoinPost(title: 'Untagged post').save(failOnError: true, flush: true)
        }
        sqlCapture.clear()

        when:
        final affected = BulkJoinPost.withTransaction {
            BulkJoinPost.where { title ==~ '%post' }.join('tags', JoinType.INNER).updateAll(title: 'Updated post')
        }
        final titles = BulkJoinPost.withTransaction {
            BulkJoinPost.list()*.title.sort()
        }

        then:
        affected == 1
        titles == ['Untagged post', 'Updated post']

        and:
        (modificationsWithASubquery('bulk_join_post') != []) == selectsFromModifiedTable

        cleanup:
        datastore?.close()

        where:
        dialect        | selectsFromModifiedTable
        H2Dialect      | true
        MySQL8Dialect  | false
        MariaDBDialect | false
    }

    void 'with #dialect.simpleName, updateAll applies an inner join requested for #description with a subquery'() {
        given:
        final datastore = createDatastore(dialect)
        sqlCapture.clear()

        when:
        final names = BulkJoinProduct.withTransaction {
            BulkJoinProduct.where { name ==~ '%product' }.join(path, JoinType.INNER).updateAll(name: 'Updated')
            (BulkJoinProduct.list()*.name - 'Updated').sort()
        }

        then:
        names == unchanged

        and: 'the subquery selects from another table'
        modificationsWithASubquery('bulk_join_product').size() == 1

        cleanup:
        datastore?.close()

        where:
        dialect       | path              | description                                        || unchanged
        MySQL8Dialect | 'category'        | 'an association to another class'                  || ['Uncategorized product']
        MySQL8Dialect | 'category.parent' | 'a path that does not lead to the queried class'   || ['Top product', 'Uncategorized product']
    }

    /**
     * Captures the SQL statements that Hibernate executes.
     */
    static class SqlCapture implements StatementInspector {

        final List<String> statements = Collections.synchronizedList(new ArrayList<String>())

        @Override
        String inspect(String sql) {
            statements.add(sql)
            sql
        }

        void clear() {
            statements.clear()
        }
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

@Entity
class BulkJoinPost {
    String title

    static hasMany = [tags: String]
}
