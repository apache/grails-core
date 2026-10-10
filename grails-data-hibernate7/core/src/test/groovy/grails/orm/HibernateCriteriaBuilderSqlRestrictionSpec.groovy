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

import org.codehaus.groovy.runtime.GroovyCategorySupport
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.dao.InvalidDataAccessResourceUsageException
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

class HibernateCriteriaBuilderSqlRestrictionSpec extends Specification {

    @Shared
    SqlRestrictionCapture sqlCapture = new SqlRestrictionCapture()

    @Shared
    @AutoCleanup
    HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            SqlRestrictionAuthor, SqlRestrictionBook, SqlRestrictionChapter, SqlRestrictionNote, SqlRestrictionEdition,
            SqlRestrictionPrinting, SqlRestrictionImprint, SqlRestrictionShape, SqlRestrictionCircle
    )

    @Shared
    PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    void setup() {
        sqlCapture.statements.clear()
    }

    @Rollback
    void 'the condition is rendered as is in parentheses, with {alias} replaced and the values bound'() {
        given:
        saveBooks()

        when:
        List<SqlRestrictionBook> books = SqlRestrictionBook.createCriteria().list {
            sqlRestriction('length({alias}.title) < ? and {alias}.pages > ?', [7, 300])
        }
        String sql = sqlCapture.statements.find { it.contains('length(') }

        then:
        books*.title == ['Dune']
        sql =~ /where \(length\((\w+)\.title\) < \? and \1\.pages > \?\)$/
    }

    @Rollback
    void 'a condition without values'() {
        given:
        saveBooks()

        expect:
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction('{alias}.pages > 300')
        }*.title == ['Dune']
    }

    @Rollback
    void '{alias} stands for the queried entity when a join adds a column with the same name'() {
        given:
        saveBooks()

        expect:
        SqlRestrictionBook.createCriteria().listDistinct {
            chapters {
                eq('title', 'Prologue')
            }
            sqlRestriction('{alias}.title = ?', ['Dune'])
        }*.title == ['Dune']
    }

    @Rollback
    void 'inside an association block {alias} stands for the association'() {
        given:
        saveBooks()

        expect:
        SqlRestrictionBook.createCriteria().listDistinct {
            chapters {
                sqlRestriction('{alias}.title = ?', ['Epilogue'])
            }
        }*.title == ['Emma']
    }

    @Rollback
    void 'a condition combined with not, or and count'() {
        given:
        saveBooks()

        expect:
        SqlRestrictionBook.createCriteria().list {
            not {
                sqlRestriction('{alias}.pages > ?', [300])
            }
        }*.title.sort() == ['Emma', 'Ulysses']
        SqlRestrictionBook.createCriteria().list {
            or {
                sqlRestriction('{alias}.pages > ?', [300])
                eq('title', 'Emma')
            }
        }*.title.sort() == ['Dune', 'Emma']
        SqlRestrictionBook.createCriteria().count {
            sqlRestriction('{alias}.pages < ?', [300])
        } == 2
    }

    @Rollback
    void 'a condition with or is grouped as a whole'() {
        given:
        saveBooks()

        expect:
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction("{alias}.title = 'Dune' or {alias}.title = 'Emma'")
            eq('title', 'Emma')
        }*.title == ['Emma']
        SqlRestrictionBook.createCriteria().list {
            not {
                sqlRestriction("{alias}.title = 'Dune' or {alias}.title = 'Emma'")
            }
        }*.title == ['Ulysses']
    }

    @Rollback
    void 'a question mark in a string literal, a quoted identifier or a comment is not a placeholder'() {
        given:
        new SqlRestrictionBook(title: 'Why?', pages: 100).save()
        new SqlRestrictionBook(title: 'Who?', pages: 10).save()
        new SqlRestrictionBook(title: "It's?", pages: 200).save(flush: true)

        expect:
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction("{alias}.title like '%?' and {alias}.pages > ?", [50])
        }*.title.sort() == ["It's?", 'Why?']
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction("{alias}.title like '%?%'")
        }*.title.sort() == ["It's?", 'Who?', 'Why?']
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction("{alias}.title = 'It''s?' and {alias}.pages > ?", [50])
        }*.title == ["It's?"]
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction('{alias}."PAGES" > ? /* why? */ and {alias}.title <> ? -- who?', [50, 'Why?'])
        }*.title == ["It's?"]
    }

    @Rollback
    void 'an association block inside a junction'() {
        given:
        saveBooks()

        expect:
        titles {
            or {
                chapters {
                    sqlRestriction('{alias}.title = ?', ['Epilogue'])
                }
                eq('title', 'Dune')
            }
        } == ['Dune', 'Emma']
        titles {
            not {
                chapters {
                    sqlRestriction('{alias}.title = ?', ['Epilogue'])
                }
            }
        } == ['Dune', 'Ulysses']
        titles {
            or {
                and {
                    chapters {
                        sqlRestriction('{alias}.title = ?', ['Epilogue'])
                    }
                }
                eq('title', 'Dune')
            }
        } == ['Dune', 'Emma']
        titles {
            or {
                chapters {
                    or {
                        sqlRestriction('{alias}.title = ?', ['Epilogue'])
                        eq('title', 'Missing')
                    }
                }
                eq('title', 'Dune')
            }
        } == ['Dune', 'Emma']
    }

    @Rollback
    void 'a junction does not use a Groovy category, which would invalidate the call sites of every thread'() {
        given:
        saveBooks()
        List<Boolean> categoryInUse = []

        when:
        List<String> found = titles {
            or {
                categoryInUse << GroovyCategorySupport.hasCategoryInAnyThread()
                chapters {
                    categoryInUse << GroovyCategorySupport.hasCategoryInAnyThread()
                    sqlRestriction('{alias}.title = ?', ['Epilogue'])
                }
                and {
                    categoryInUse << GroovyCategorySupport.hasCategoryInAnyThread()
                    eq('title', 'Dune')
                }
            }
            not {
                categoryInUse << GroovyCategorySupport.hasCategoryInAnyThread()
                eq('title', 'Ulysses')
            }
        }

        then:
        found == ['Dune', 'Emma']
        categoryInUse == [false, false, false, false]
    }

    @Rollback
    void 'a detached criteria'() {
        given:
        saveBooks()
        DetachedCriteria<SqlRestrictionBook> longBooks = new DetachedCriteria(SqlRestrictionBook).build {
            sqlRestriction('{alias}.pages > ?', [200])
        }

        expect:
        longBooks.list()*.title.sort() == ['Dune', 'Emma']
        longBooks.count() == 2
        longBooks.list { eq('title', 'Emma') }*.title == ['Emma']
        new DetachedCriteria(SqlRestrictionBook).build {
            sqlRestriction('{alias}.title = ?', ['Dune'])
        }.get().pages == 412
        new DetachedCriteria(SqlRestrictionBook).build {
            chapters {
                sqlRestriction('{alias}.title = ?', ['Epilogue'])
            }
        }.list()*.title == ['Emma']
        new DetachedCriteria(SqlRestrictionBook).build {
            or {
                sqlRestriction('{alias}.pages < ?', [200])
                eq('title', 'Dune')
            }
        }.list()*.title.sort() == ['Dune', 'Ulysses']
    }

    @Rollback
    void 'a detached criteria as a subquery, where {alias} stands for the entity of the subquery'() {
        given:
        saveBooks()
        DetachedCriteria<SqlRestrictionChapter> epilogueBookIds = new DetachedCriteria(SqlRestrictionChapter).build {
            sqlRestriction('{alias}.title = ?', ['Epilogue'])
            projections {
                property('book.id')
            }
        }

        expect:
        SqlRestrictionBook.createCriteria().list {
            inList('id', epilogueBookIds)
        }*.title == ['Emma']
    }

    @Rollback
    void 'a batch operation on a detached criteria does not support a condition'() {
        given:
        saveBooks()
        DetachedCriteria<SqlRestrictionBook> longBooks = new DetachedCriteria(SqlRestrictionBook).build {
            sqlRestriction('{alias}.pages > ?', [200])
        }

        when:
        longBooks.updateAll(pages: 1)

        then:
        InvalidDataAccessResourceUsageException updateFailure = thrown()
        updateFailure.message.contains('SqlRestriction')

        when:
        longBooks.deleteAll()

        then:
        InvalidDataAccessResourceUsageException deleteFailure = thrown()
        deleteFailure.message.contains('SqlRestriction')
        SqlRestrictionBook.list()*.pages.sort() == [120, 250, 412]
    }

    @Rollback
    void 'nested association blocks'() {
        given:
        saveBooks()

        expect:
        titles {
            chapters {
                notes {
                    sqlRestriction('{alias}.text = ?', ['footnote'])
                }
            }
        } == ['Emma']

        and: 'inside a junction, the same as the equivalent criterion'
        titles {
            or {
                chapters {
                    notes {
                        sqlRestriction('{alias}.text = ?', ['footnote'])
                    }
                }
                eq('title', 'Dune')
            }
        } == titles {
            or {
                chapters {
                    notes {
                        eq('text', 'footnote')
                    }
                }
                eq('title', 'Dune')
            }
        }
    }

    @Rollback
    void 'an entity with a composite identifier'() {
        given:
        new SqlRestrictionEdition(isbn: '978-0', number: 1, label: 'first').save()
        new SqlRestrictionEdition(isbn: '978-0', number: 2, label: 'second').save(flush: true)

        expect:
        SqlRestrictionEdition.createCriteria().list {
            sqlRestriction('{alias}.label = ?', ['second'])
        }*.number == [2]
    }

    @Rollback
    void 'an entity whose composite identifier starts with an association'() {
        given:
        SqlRestrictionAuthor author = new SqlRestrictionAuthor(name: 'Austen').save()
        new SqlRestrictionPrinting(author: author, code: 'A', place: 'London').save()
        new SqlRestrictionPrinting(author: author, code: 'B', place: 'Bath').save(flush: true)

        expect:
        SqlRestrictionPrinting.createCriteria().list {
            sqlRestriction('{alias}.place = ?', ['Bath'])
        }*.code == ['B']
    }

    @Rollback
    void 'an entity whose composite identifier starts with an association to an entity with a composite identifier'() {
        given:
        SqlRestrictionEdition edition = new SqlRestrictionEdition(isbn: '978-0', number: 1, label: 'first').save()
        new SqlRestrictionImprint(edition: edition, code: 'A', place: 'London').save()
        new SqlRestrictionImprint(edition: edition, code: 'B', place: 'Bath').save(flush: true)

        expect:
        SqlRestrictionImprint.createCriteria().list {
            sqlRestriction('{alias}.place = ?', ['Bath'])
        }*.code == ['B']
        SqlRestrictionImprint.createCriteria().list {
            sqlRestriction('place = ?', ['Bath'])
        }*.code == ['B']
    }

    @Rollback
    void 'a subclass mapped to its own table'() {
        given:
        new SqlRestrictionShape(name: 'square').save()
        new SqlRestrictionCircle(name: 'small', radius: 1).save()
        new SqlRestrictionCircle(name: 'large', radius: 10).save(flush: true)

        expect: '{alias} stands for the table of the queried class'
        SqlRestrictionCircle.createCriteria().list {
            sqlRestriction('{alias}.radius > ?', [5])
        }*.name == ['large']
    }

    void 'the number of ? placeholders must match the number of values'() {
        when:
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction(sql, values)
        }

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(message)

        where:
        sql                 | values || message
        '{alias}.pages > ?' | []     || 'has 1 ? placeholders but 0 values'
        '{alias}.pages > ?' | [1, 2] || 'has 1 ? placeholders but 2 values'
        '{alias}.pages > 1' | [1]    || 'has 0 ? placeholders but 1 values'
    }

    void 'a null value is rejected'() {
        when:
        SqlRestrictionBook.createCriteria().list {
            sqlRestriction('{alias}.title = ?', [null])
        }

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('must not be null')
    }

    private static void saveBooks() {
        new SqlRestrictionBook(title: 'Dune', pages: 412).addToChapters(title: 'Prologue').save()
        new SqlRestrictionBook(title: 'Emma', pages: 250)
                .addToChapters(new SqlRestrictionChapter(title: 'Epilogue').addToNotes(text: 'footnote'))
                .save()
        new SqlRestrictionBook(title: 'Ulysses', pages: 120).addToChapters(title: 'Telemachus').save(flush: true)
    }

    private static List<String> titles(Closure criteria) {
        SqlRestrictionBook.createCriteria().listDistinct(criteria)*.title.sort()
    }
}

class SqlRestrictionCapture implements StatementInspector {

    final List<String> statements = Collections.synchronizedList([])

    @Override
    String inspect(String sql) {
        statements << sql
        sql
    }
}

@Entity
class SqlRestrictionBook {

    String title
    Integer pages
    static hasMany = [chapters: SqlRestrictionChapter]
}

@Entity
class SqlRestrictionChapter {

    String title
    static belongsTo = [book: SqlRestrictionBook]
    static hasMany = [notes: SqlRestrictionNote]
}

@Entity
class SqlRestrictionNote {

    String text
    static belongsTo = [chapter: SqlRestrictionChapter]
}

@Entity
class SqlRestrictionEdition implements Serializable {

    String isbn
    Integer number
    String label
    static mapping = {
        id(composite: ['isbn', 'number'])
    }
}

@Entity
class SqlRestrictionAuthor {

    String name
}

@Entity
class SqlRestrictionPrinting implements Serializable {

    SqlRestrictionAuthor author
    String code
    String place
    static mapping = {
        id(composite: ['author', 'code'])
    }
}

@Entity
class SqlRestrictionImprint implements Serializable {

    SqlRestrictionEdition edition
    String code
    String place
    static mapping = {
        id(composite: ['edition', 'code'])
    }
}

@Entity
class SqlRestrictionShape {

    String name
    static mapping = {
        tablePerHierarchy(false)
    }
}

@Entity
class SqlRestrictionCircle extends SqlRestrictionShape {

    Integer radius
}
