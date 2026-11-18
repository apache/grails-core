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

import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.query.SqlSyntax

/**
 * The native SQL of a detached criteria is read in the syntax of the connection it runs on, not of the default
 * connection: an entity mapped to H2 by default and to SQL Server by name reads {@code [width?] = ?} as an identifier
 * with a question mark in its name on the SQL Server connection, and as an array literal with a placeholder on H2.
 */
class HibernateCriteriaBuilderConnectionSyntaxSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'dataSource.url': 'jdbc:h2:mem:connectionSyntaxDefault;LOCK_TIMEOUT=10000',
                    'dataSources.sqlserver.url': 'jdbc:h2:mem:connectionSyntaxSqlServer;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;LOCK_TIMEOUT=10000',
                    'dataSources.sqlserver.dialect': 'org.hibernate.dialect.SQLServerDialect',
                    'dataSources.sqlserver.dbCreate': 'create-drop'
            ),
            ConnectionSyntaxBox
    )

    void 'each connection resolves its own syntax'() {
        expect:
        SqlSyntax.forSessionFactory(datastore.getSessionFactory()) == SqlSyntax.H2
        SqlSyntax.forSessionFactory(datastore.getSessionFactory('sqlserver')) == SqlSyntax.TRANSACT_SQL
    }

    void 'a detached criteria reads a bracket in the syntax of the connection it runs on'() {
        given: "a row on each connection"
        ConnectionSyntaxBox.withTransaction { new ConnectionSyntaxBox(width: 4).save(flush: true) }
        ConnectionSyntaxBox.sqlserver.withTransaction { new ConnectionSyntaxBox(width: 4).sqlserver.save(flush: true) }

        when: "a criteria with an array literal runs on the default connection, where a bracket holds an array"
        DetachedCriteria<ConnectionSyntaxBox> criteria = new DetachedCriteria(ConnectionSyntaxBox).build {
            sqlRestriction('{alias}.width = ANY(ARRAY[CAST(? AS INTEGER), CAST(? AS INTEGER)])', [4, 5])
        }
        List<ConnectionSyntaxBox> onDefault = ConnectionSyntaxBox.withNewSession { criteria.list() }

        then: "two placeholders in an array literal are counted and bound"
        onDefault*.width == [4]

        when: "a bracket identifier whose name has a question mark runs on the SQL Server connection, where it holds no placeholder"
        criteria = new DetachedCriteria(ConnectionSyntaxBox).withConnection('sqlserver').build {
            sqlRestriction('{alias}.[width] in (select [width] as [a?b] from connection_syntax_box)')
        }
        List<ConnectionSyntaxBox> onSqlServer = ConnectionSyntaxBox.sqlserver.withNewSession { criteria.list() }

        then:
        onSqlServer*.width == [4]

        when: "the count of placeholders is wrong for the syntax of the connection"
        ConnectionSyntaxBox.sqlserver.withNewSession {
            new DetachedCriteria(ConnectionSyntaxBox).withConnection('sqlserver').build {
                sqlRestriction('{alias}.[wid?th] > ?', [3, 4])
            }.list()
        }

        then: "it is rejected when the query runs, with the placeholders the connection's syntax counts"
        Exception e = thrown()
        ExceptionCauses.of(e).any { it.message?.contains('has 1 ? placeholders but 2 values') }
    }
}

@Entity
class ConnectionSyntaxBox {

    Integer width

    static mapping = {
        datasource 'ALL'
    }
}
