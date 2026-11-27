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
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The {@code unique} constraint of an entity mapped to every connection checks the connection a connection block
 * names, as the unqualified calls inside the block do, and the default connection outside any block.
 */
@Issue('https://github.com/apache/grails-core/issues/16934')
class UniqueConstraintConnectionScopeHibernate7Spec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'      : 'jdbc:h2:mem:uniqueScopeDefault;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate' : 'create-drop',
            'dataSource.dialect'  : H2Dialect.name,
            'dataSources.second'  : [url: 'jdbc:h2:mem:uniqueScopeSecond;LOCK_TIMEOUT=10000'],
            'dataSources.third'   : [url: 'jdbc:h2:mem:uniqueScopeThird;LOCK_TIMEOUT=10000']
    ]), UnqScopeEntity, UnqScopeAnimal, UnqScopeDog, UnqScopeAuthor, UnqScopeBook)

    void cleanup() {
        [UnqScopeBook, UnqScopeAuthor, UnqScopeAnimal].each { Class type ->
            type.withNewTransaction { type.executeUpdate("delete from ${type.simpleName}".toString()) }
            type.second.withNewTransaction { type.second.executeUpdate("delete from ${type.simpleName}".toString()) }
        }
        UnqScopeEntity.withNewTransaction { UnqScopeEntity.executeUpdate('delete from UnqScopeEntity') }
        UnqScopeEntity.second.withNewTransaction { UnqScopeEntity.second.executeUpdate('delete from UnqScopeEntity') }
        UnqScopeEntity.third.withNewTransaction { UnqScopeEntity.third.executeUpdate('delete from UnqScopeEntity') }
    }

    void 'a duplicate that exists only on the connection of the block is rejected inside the block'() {
        given: 'a row on the second connection only'
        UnqScopeEntity.second.withNewTransaction {
            new UnqScopeEntity(name: 'taken').second.save(validate: false, flush: true)
        }

        expect: 'the block checks the second connection'
        UnqScopeEntity.second.withNewSession {
            !new UnqScopeEntity(name: 'taken').validate()
        }

        and: 'the default connection, which has no such row, accepts it'
        UnqScopeEntity.withNewSession {
            new UnqScopeEntity(name: 'taken').validate()
        }
    }

    void 'the check runs on the connection the save names, not on the block around it'() {
        given: 'a row on the second connection only'
        UnqScopeEntity.second.withNewTransaction {
            new UnqScopeEntity(name: 'taken').second.save(validate: false, flush: true)
        }

        when: 'the save names the third connection inside a block of the second, with a session open for the third'
        UnqScopeEntity book = new UnqScopeEntity(name: 'taken')
        UnqScopeEntity saved = UnqScopeEntity.third.withNewSession {
            UnqScopeEntity.second.withNewSession {
                book.third.save()
            }
        }

        then: 'the third has no such row, so the save goes through'
        book.errors.allErrors.toString() == '[]'
        saved != null
    }

    void 'a save through the API of the default connection checks the default connection, not the block around it'() {
        given: 'a row on the second connection only'
        UnqScopeEntity.second.withNewTransaction {
            new UnqScopeEntity(name: 'taken').second.save(validate: false, flush: true)
        }

        when: 'the default connection API saves it inside a block of the second, with a session open for the default'
        UnqScopeEntity book = new UnqScopeEntity(name: 'taken')
        UnqScopeEntity saved = UnqScopeEntity.withNewSession {
            UnqScopeEntity.second.withNewSession {
                UnqScopeEntity.DEFAULT.save(book)
            }
        }

        then: 'the default connection has no such row, so the save goes through'
        book.errors.allErrors.toString() == '[]'
        saved != null
    }

    void 'a save through the API of the second connection checks the second connection outside any block'() {
        given: 'a row on the second connection only'
        UnqScopeEntity.second.withNewTransaction {
            new UnqScopeEntity(name: 'taken').second.save(validate: false, flush: true)
        }

        expect: 'the second connection rejects it'
        UnqScopeEntity.withNewSession {
            UnqScopeEntity.second.withNewSession {
                UnqScopeEntity.second.save(new UnqScopeEntity(name: 'taken')) == null
            }
        }
    }

    void 'a subclass is checked on the connection of its own block, and of the block of its parent'() {
        given: 'one tag on the second connection only and one on the default connection only'
        UnqScopeAnimal.second.withNewTransaction {
            new UnqScopeDog(tag: 'onSecondTag').second.save(validate: false, flush: true)
        }
        UnqScopeAnimal.withNewTransaction {
            new UnqScopeDog(tag: 'onDefaultTag').save(validate: false, flush: true)
        }

        expect: 'the block of the subclass checks the second connection'
        UnqScopeDog.withNewSession {
            UnqScopeDog.second.withNewSession {
                !new UnqScopeDog(tag: 'onSecondTag').validate() && new UnqScopeDog(tag: 'onDefaultTag').validate()
            }
        }

        and: 'the block of the parent routes the parent only, so the subclass is checked where it is saved, on the default connection'
        UnqScopeAnimal.withNewSession {
            UnqScopeAnimal.second.withNewSession {
                new UnqScopeDog(tag: 'onSecondTag').validate() && !new UnqScopeDog(tag: 'onDefaultTag').validate()
            }
        }
    }

    void 'a cascaded instance is checked on the connection of the save that names it'() {
        given: 'a title on the second connection only'
        UnqScopeBook.second.withNewTransaction {
            new UnqScopeBook(title: 'onSecondTitle').second.save(validate: false, flush: true)
        }

        when: 'the author is validated through the API of the second connection, with a book that has the title'
        UnqScopeAuthor author = new UnqScopeAuthor(name: 'a')
        author.addToBooks(new UnqScopeBook(title: 'onSecondTitle'))
        boolean validOnSecond = UnqScopeAuthor.withNewSession {
            UnqScopeAuthor.second.withNewSession {
                UnqScopeAuthor.second.save(author) != null
            }
        }

        then: 'the book is checked on the second connection too, and refused'
        !validOnSecond
    }

    void 'a duplicate that exists only on the default connection is accepted inside the block of the second'() {
        given: 'a row on the default connection only'
        UnqScopeEntity.withNewTransaction {
            new UnqScopeEntity(name: 'taken').save(validate: false, flush: true)
        }

        expect:
        UnqScopeEntity.second.withNewSession {
            new UnqScopeEntity(name: 'taken').validate()
        }

        and: 'the default connection rejects it'
        UnqScopeEntity.withNewSession {
            !new UnqScopeEntity(name: 'taken').validate()
        }
    }
}

@Entity
class UnqScopeEntity {

    String name

    static constraints = {
        name(unique: true)
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}

@Entity
class UnqScopeAnimal {

    String tag

    static constraints = {
        tag(unique: true)
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}

@Entity
class UnqScopeDog extends UnqScopeAnimal {

    static mapping = {
        datasource(ConnectionSource.ALL)
    }
}

@Entity
class UnqScopeAuthor {

    String name

    static hasMany = [books: UnqScopeBook]

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}

@Entity
class UnqScopeBook {

    String title

    static belongsTo = [author: UnqScopeAuthor]

    static constraints = {
        title(unique: true)
        author(nullable: true)
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}
