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
package grails.gorm.tests.hasmany.jointablenaming

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.cfg.ImprovedNamingStrategy
import spock.lang.Issue
import spock.lang.Specification

class JoinTableNamingStrategySpec extends Specification {

    HibernateDatastore datastore

    void cleanup() {
        datastore?.close()
        GrailsDomainBinder.configureNamingStrategy(ImprovedNamingStrategy.INSTANCE)
    }

    @Issue('https://github.com/apache/grails-core/issues/15736')
    void 'class name prefix is removed from entity and join table names'() {
        given:
        datastore = new HibernateDatastore([
                (Settings.SETTING_DB_CREATE): 'create-drop',
                'hibernate.naming_strategy': MyappClassPrefixNamingStrategy
        ], MyappAuthor, MyappBook)

        when:
        def columnsByTable = readColumnsByTable()

        then:
        columnsByTable['book'].containsAll('id', 'version', 'title')
        columnsByTable['author'].containsAll('id', 'version', 'name')
        columnsByTable['author_books'].containsAll('author_id', 'book_id')
        !columnsByTable.containsKey('myapp_book')
        !columnsByTable.containsKey('myapp_author')
    }

    @Issue('https://github.com/apache/grails-core/issues/15736')
    void 'entity and join table names use a prefix from the naming strategy'() {
        given:
        datastore = new HibernateDatastore([
                (Settings.SETTING_DB_CREATE): 'create-drop',
                'hibernate.naming_strategy': MyappTablePrefixNamingStrategy
        ], Author, Book)

        when:
        def columnsByTable = readColumnsByTable()

        then:
        columnsByTable['myapp_book'].containsAll('id', 'version', 'title')
        columnsByTable['myapp_author'].containsAll('id', 'version', 'name')
        columnsByTable['myapp_author_books'].containsAll('myapp_author_id', 'myapp_book_id')
        !columnsByTable.containsKey('book')
        !columnsByTable.containsKey('author')
    }

    private Map<String, Set<String>> readColumnsByTable() {
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { connection ->
            Map<String, Set<String>> columns = [:].withDefault { [] as Set<String> }
            connection.metaData.getColumns(null, null, null, null).withCloseable { resultSet ->
                while (resultSet.next()) {
                    columns[resultSet.getString('TABLE_NAME').toLowerCase()] << resultSet.getString('COLUMN_NAME').toLowerCase()
                }
            }
            columns
        }
    }
}

class MyappClassPrefixNamingStrategy extends ImprovedNamingStrategy {

    @Override
    String classToTableName(String className) {
        super.classToTableName(className.replaceFirst('^Myapp', ''))
    }
}

class MyappTablePrefixNamingStrategy extends ImprovedNamingStrategy {

    @Override
    String classToTableName(String className) {
        "myapp_${super.classToTableName(className)}"
    }
}

@Entity
class MyappAuthor {
    String name

    static hasMany = [books: MyappBook]
}

@Entity
class MyappBook {
    String title

    static hasMany = [authors: MyappAuthor]
}

@Entity
class Author {
    String name

    static hasMany = [books: Book]
}

@Entity
class Book {
    String title

    static hasMany = [authors: Author]
}
