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
package grails.orm

import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.query.SqlSyntax

import static org.hibernate.type.StandardBasicTypes.INTEGER

/**
 * The native SQL of a criteria query read as SQL Server reads it: H2 in its SQL Server compatibility mode, which
 * quotes identifiers with square brackets, under Hibernate's SQL Server dialect, from which the syntax is resolved.
 */
class HibernateCriteriaBuilderTransactSqlSpec extends Specification {

    @Shared
    TransactSqlCapture sqlCapture = new TransactSqlCapture()

    @Shared
    @AutoCleanup
    HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'dataSource.url': 'jdbc:h2:mem:transactSql;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1',
                    'hibernate.dialect': 'org.hibernate.dialect.SQLServerDialect',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            TransactSqlBox
    )

    @Shared
    PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    void setup() {
        sqlCapture.statements.clear()
    }

    void 'the syntax is resolved from the dialect wherever a query is built'() {
        expect:
        SqlSyntax.forSessionFactory(hibernateDatastore.sessionFactory) == SqlSyntax.TRANSACT_SQL
        SqlSyntax.forCriteriaBuilder(hibernateDatastore.sessionFactory.criteriaBuilder) == SqlSyntax.TRANSACT_SQL
    }

    @Rollback
    void 'in SQL Server mode a bracket identifier in a restriction is rendered as written, and a question mark inside it is not a placeholder'() {
        given:
        saveBoxes()

        expect:
        TransactSqlBox.createCriteria().list {
            sqlRestriction('{alias}.[width] > ? and [height] <> ?', [2, 100])
        }*.label == ['C']
        TransactSqlBox.createCriteria().list {
            sqlRestriction("{alias}.[width] = ? and {alias}.[label] <> '[?]'", [2])
            order('height')
        }*.label == ['A', 'A', 'B']

        when:
        TransactSqlBox.createCriteria().list {
            sqlRestriction('{alias}.[width?] = ?', [2, 3])
        }

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('has 1 ? placeholders but 2 values')
    }

    @Rollback
    void 'in SQL Server mode a detached criteria reads its restriction the same way'() {
        given:
        saveBoxes()

        expect:
        new DetachedCriteria(TransactSqlBox).build {
            sqlRestriction('{alias}.[width] > ?', [2])
        }.list()*.label == ['C']

        when: "the number of placeholders is checked where the SQL is rendered, on the connection it runs on"
        new DetachedCriteria(TransactSqlBox).build {
            sqlRestriction('{alias}.[width?] = ?', [2, 3])
        }.list()

        then:
        Exception e = thrown()
        ExceptionCauses.of(e).any { it.message?.contains('has 1 ? placeholders but 2 values') }
    }

    @Rollback
    void 'in SQL Server mode a column alias in brackets, holding any character, is removed from the projected SQL and matched in the group by clause'() {
        given:
        saveBoxes()

        when:
        List rows = TransactSqlBox.createCriteria().list {
            projections {
                sqlGroupProjection "[width] as [box's width], sum([height]) as [total height]", '[BOX\'S WIDTH]',
                        ["box's width", 'total height'], [INTEGER, INTEGER]
            }
            order("box's width")
        }
        String sql = sqlCapture.statements.last()

        then:
        rows*.toList() == [[2, 24], [4, 9]]
        sql =~ /(?i)group by \[width\]/
        !sql.contains("box's width")
    }

    private static void saveBoxes() {
        new TransactSqlBox(label: 'A', width: 2, height: 7).save()
        new TransactSqlBox(label: 'A', width: 2, height: 8).save()
        new TransactSqlBox(label: 'B', width: 2, height: 9).save()
        new TransactSqlBox(label: 'C', width: 4, height: 9).save(flush: true)
    }
}

class TransactSqlCapture implements StatementInspector {

    final List<String> statements = Collections.synchronizedList([])

    @Override
    String inspect(String sql) {
        statements << sql
        sql
    }
}

@Entity
class TransactSqlBox {

    String label
    Integer width
    Integer height
}
