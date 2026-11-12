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

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.config.UniqueNulls
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The {@code unique} constraint reads a {@code null} in a key as the unique index of the connection the check runs on
 * does, so that one application whose connections are on different databases validates each as that database indexes.
 * The default connection is H2 in its regular mode, which indexes a partly null key as {@code skip}; the second is H2 in
 * {@code MODE=Oracle}, which indexes it as {@code value}.
 */
class UniqueNullsConnectionHibernate7Spec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'      : 'jdbc:h2:mem:uniqueNullsDefault;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate' : 'create-drop',
            'dataSource.dialect'  : H2Dialect.name,
            'dataSources.second'  : [url: 'jdbc:h2:mem:uniqueNullsSecond;MODE=Oracle;LOCK_TIMEOUT=10000']
    ]), UnqConnDefault, UnqConnSecond)

    void 'each connection reads nulls as its own database indexes them'() {
        expect:
        datastore.databaseUniqueNulls == UniqueNulls.SKIP
        datastore.getDatastoreForConnection('second').databaseUniqueNulls == UniqueNulls.VALUE
    }

    @Issue('https://github.com/apache/grails-core/pull/16583')
    void 'an entity mapped to the connection that indexes nulls as values is validated with that reading, and one on the default connection with its own'() {
        given: 'the same partly null key saved on the connection of each entity, without validation'
        UnqConnDefault.withNewTransaction {
            new UnqConnDefault(language: 'en', country: null).save(validate: false, flush: true)
        }
        UnqConnSecond.withNewTransaction {
            new UnqConnSecond(language: 'en', country: null).save(validate: false, flush: true)
        }

        expect: 'the default connection reads skip, so the duplicate is accepted'
        UnqConnDefault.withNewSession {
            new UnqConnDefault(language: 'en', country: null).validate()
        }

        and: 'the second reads value, as its unique index does, so the duplicate is rejected'
        UnqConnSecond.withNewSession {
            !new UnqConnSecond(language: 'en', country: null).validate()
        }
    }
}

@Entity
class UnqConnDefault {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: 'country')
        country(nullable: true)
    }

    static mapping = {
        version false
    }
}

@Entity
class UnqConnSecond {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: 'country')
        country(nullable: true)
    }

    static mapping = {
        version false
        datasource('second')
    }
}
