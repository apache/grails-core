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
package org.grails.orm.hibernate.query

import org.hibernate.query.SyntaxException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings

/**
 * Pins what Hibernate's own HQL parser accepts as a comment, which is what {@link SqlSyntax#HQL} reads: block comments
 * only. A line comment of native SQL, {@code --}, and of other languages, {@code //}, is a syntax error in HQL.
 */
class HqlCommentsSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver((Settings.SETTING_DB_CREATE): 'create-drop'), HqlCommentBook)

    void 'a block comment is a comment in HQL'() {
        expect:
        datastore.sessionFactory.openSession().withCloseable {
            it.createQuery('select b.width from HqlCommentBook b /* a comment */ where b.height > 0').list() == []
        }
    }

    @Unroll
    void 'the line comment #marker is not a comment in HQL'() {
        when:
        datastore.sessionFactory.openSession().withCloseable {
            it.createQuery("select b.width from HqlCommentBook b ${marker} a comment\nwhere b.height > 0".toString()).list()
        }

        then: "Hibernate reports the syntax error, wrapped"
        IllegalArgumentException e = thrown()
        e.cause instanceof SyntaxException

        where:
        marker << ['--', '//']
    }

    void 'a double minus is a subtraction in HQL, so the select that follows it is the select of the query'() {
        expect: 'Hibernate parses the double minus as two minus signs of an expression'
        datastore.sessionFactory.openSession().withCloseable {
            it.createQuery('select b.width from HqlCommentBook b where b.height--1 > 0').queryString ==
                    'select b.width from HqlCommentBook b where b.height--1 > 0'
        }

        and: 'HqlText reads the select after it'
        HqlText.of('from HqlCommentBook b where b.height--1 > 0 select b.width').projectionCount() == 1
        HqlText.of('from HqlCommentBook b where b.height--1 > 0 select b.width').singleProjectionClause() == 'b.width'
    }
}

@Entity
class HqlCommentBook {

    Integer width
    Integer height
}
