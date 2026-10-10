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

import java.sql.Time
import java.time.LocalDateTime

import org.hibernate.resource.jdbc.spi.StatementInspector
import org.hibernate.type.BasicTypeRegistry
import org.hibernate.type.StandardBasicTypes
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings

class HibernateCriteriaBuilderSqlProjectionSpec extends Specification {

    @Shared
    SqlProjectionCapture sqlCapture = new SqlProjectionCapture()

    @Shared
    @AutoCleanup
    HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            SqlProjectionBox, SqlProjectionShelf
    )

    @Shared
    PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    void setup() {
        sqlCapture.statements.clear()
    }

    @Rollback
    void 'a single value is projected with a type constant of the DSL'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlProjection 'sum(width * height) as totalArea', 'totalArea', INTEGER
            }
        } == [84]
    }

    @Rollback
    void 'several values are projected, one column each, as on Hibernate 5'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlProjection '(2 * (width + height)) as perimeter, (width * height) as area',
                        ['perimeter', 'area'], [INTEGER, INTEGER]
            }
            order('height')
        }

        then:
        rows*.toList() == [[18, 14], [20, 16], [22, 18], [26, 36]]
    }

    @Rollback
    void 'the results are grouped by the group by clause'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width, sum(height) as combinedHeightsForThisWidth', 'width',
                        ['width', 'combinedHeightsForThisWidth'], [INTEGER, INTEGER]
            }
        }

        then:
        rows*.toList().sort { it[0] } == [[2, 24], [4, 9]]
        sqlCapture.statements.last() =~ /(?i)group by width$/
    }

    @Rollback
    void 'a grouped SQL projection combines with the other projections'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width as boxWidth', 'width', ['boxWidth'], [INTEGER]
                rowCount()
                sum('height')
            }
        }

        then:
        rows*.toList().sort { it[0] } == [[2, 3L, 24L], [4, 1L, 9L]]
    }

    @Rollback
    void 'the results are ordered by the alias of a SQL projection'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width, sum(height) as total', 'width', ['width', 'total'], [INTEGER, INTEGER]
            }
            order('total', 'desc')
        }

        then:
        rows*.toList() == [[2, 24], [4, 9]]
    }

    @Rollback
    void '{alias} stands for the queried entity when a join adds a column with the same name'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            shelf {
                eq('label', 'Top')
            }
            projections {
                sqlGroupProjection 'upper({alias}.label) as boxLabel', 'upper({alias}.label)', ['boxLabel'], [STRING]
                rowCount()
            }
        }
        String sql = sqlCapture.statements.last()

        then:
        rows*.toList().sort { it[0] } == [['A', 2L], ['B', 1L]]
        sql =~ /(?i)select upper\((\w+)\.label\),count\(\1\.id\) from sql_projection_box \1 join sql_projection_shelf/
    }

    @Rollback
    void 'rows are bucketed by a SQL expression of a timestamp'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection "date_trunc('DAY', {alias}.packed_at) as packedDay", "date_trunc('DAY', {alias}.packed_at)",
                        ['packedDay'], [TIMESTAMP]
                rowCount()
            }
        }

        then:
        rows.size() == 2
        rows.every { it[0] instanceof Date }
        rows*.getAt(1).sort() == [1L, 3L]
    }

    @Rollback
    void 'a comma inside parentheses or a string literal does not separate columns'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlProjection "concat(coalesce({alias}.label, 'x'), ',', 'y') as tag", 'tag', STRING
            }
            order('height')
        } == ['A,y', 'A,y', 'B,y', 'C,y']
    }

    @Rollback
    void 'a comma inside a dollar-quoted string does not separate columns'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlProjection '$$a,b$$ as tag, height as height', ['tag', 'height'], [STRING, INTEGER]
            }
            order('height')
        }*.toList() == [['a,b', 7], ['a,b', 8], ['a,b', 9], ['a,b', 9]]
    }

    @Rollback
    void 'the type may be a StandardBasicTypes constant or a Java class'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().get {
            projections {
                sqlProjection 'max(height)', ['tallest'], [StandardBasicTypes.LONG]
            }
        } == 9L
        SqlProjectionBox.createCriteria().get {
            projections {
                sqlProjection 'min(height)', ['shortest'], [Integer]
            }
        } == 7
    }

    @Rollback
    void 'the type may be a Hibernate type, which tells how the value is read'() {
        given:
        saveBoxes()
        BasicTypeRegistry types = hibernateDatastore.sessionFactory.typeConfiguration.basicTypeRegistry

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlProjection "cast({alias}.packed_at as date) as packedOn, case when {alias}.height > 8 then 'Y' else 'N' end as tall",
                        ['packedOn', 'tall'], [types.resolve(StandardBasicTypes.DATE), types.resolve(StandardBasicTypes.YES_NO)]
            }
            order('height')
        }

        then:
        rows.every { it[0] instanceof java.sql.Date }
        rows*.getAt(1) == [false, false, true, true]
    }

    @Rollback
    void 'a SQL projection applies to the rows the criteria select'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            gt('width', 2)
            projections {
                sqlProjection 'sum(width * height) as totalArea', 'totalArea', INTEGER
            }
        } == [36]
    }

    @Rollback
    void 'a restriction on a property is not resolved to a SQL projection with the same alias'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            gt('height', 7)
            projections {
                sqlProjection 'sum(height) as height', 'height', INTEGER
            }
        } == [26]
    }

    @Rollback
    void 'a restriction on an association is not resolved to a SQL projection with the same alias'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            shelf {
                eq('label', 'Top')
            }
            projections {
                sqlGroupProjection 'upper({alias}.label) as label', 'upper({alias}.label)', ['label'], [STRING]
                rowCount()
            }
        }

        then:
        rows*.toList().sort { it[0] } == [['A', 2L], ['B', 1L]]
    }

    @Rollback
    void 'the group by clause may name the column alias of a projected value'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width * 10 as scaledWidth, sum(height) as total', 'scaledWidth',
                        ['scaledWidth', 'total'], [INTEGER, INTEGER]
            }
            order('total')
        }

        then:
        rows*.toList() == [[40, 9], [20, 24]]
    }

    @Rollback
    void 'a column alias in backquotes or brackets is removed from the projected SQL'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width as [boxWidth], sum(height) as `total`', 'width', ['boxWidth', 'total'],
                        [INTEGER, INTEGER]
            }
            order('total', 'desc')
        }*.toList() == [[2, 24], [4, 9]]
    }

    @Rollback
    void 'a quoted column alias may hold any character'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width * 10 as "scaled width", sum(height) as [total height], max(height) as "tallest ""box"""',
                        '"scaled width"', ['scaled width', 'total height', 'tallest "box"'], [INTEGER, INTEGER, INTEGER]
            }
            order('total height')
        }
        String sql = sqlCapture.statements.last()

        then:
        rows*.toList() == [[40, 9, 9], [20, 24, 9]]
        sql =~ /(?i)group by width \* 10\b/
        !sql.contains('"')
        !sql.contains('[')
    }

    @Rollback
    void 'a comment after a column alias is removed with the alias'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width as boxWidth /* a comment */, sum(height) as total -- another comment\n', 'boxWidth',
                        ['boxWidth', 'total'], [INTEGER, INTEGER]
            }
            order('boxWidth')
        }*.toList() == [[2, 24], [4, 9]]
    }

    @Rollback
    void 'quoted column aliases that differ only in case are grouped by apart'() {
        given:
        saveBoxes()

        expect:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width as "key", height as "KEY", count(*) as total', '"key", "KEY"',
                        ['key', 'KEY', 'total'], [INTEGER, INTEGER, LONG]
            }
            order('key')
            order('KEY')
        }*.toList() == [[2, 7, 1L], [2, 8, 1L], [2, 9, 1L], [4, 9, 1L]]
    }

    @Rollback
    void 'a column alias in #quotes in the group by clause is matched regardless of case'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection sql, groupBy, ['boxWidth', 'total'], [INTEGER, INTEGER]
            }
            order('boxWidth')
        }

        then:
        rows*.toList() == [[2, 24], [4, 9]]
        sqlCapture.statements.last() =~ /(?i)group by width\b/

        where:
        quotes            | sql                                         | groupBy
        'backquotes'      | 'width as `boxWidth`, sum(height) as total' | '`BOXWIDTH`'
        'square brackets' | 'width as [boxWidth], sum(height) as total' | '[BOXWIDTH]'
    }

    @Rollback
    void 'a date or a time is read as its SQL type'() {
        given:
        saveBoxes()

        when:
        List rows = SqlProjectionBox.createCriteria().list {
            projections {
                sqlProjection 'cast({alias}.packed_at as date) as packedOn, cast({alias}.packed_at as time) as packedTime',
                        ['packedOn', 'packedTime'], [DATE, TIME]
            }
            order('height')
        }

        then:
        rows.every { it[0] instanceof java.sql.Date && it[1] instanceof Time }
        rows[0][1].toString() == '09:30:00'
    }

    void 'an invalid SQL projection releases the session the criteria query opened'() {
        given:
        def sessionFactory = hibernateDatastore.sessionFactory
        assert !TransactionSynchronizationManager.hasResource(sessionFactory)

        when:
        SqlProjectionBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width', ' ', ['width'], [INTEGER]
            }
        }

        then:
        thrown(IllegalArgumentException)
        !TransactionSynchronizationManager.hasResource(sessionFactory)
    }

    void 'the numbers of projected columns, aliases and types must match'() {
        when:
        SqlProjectionBox.withNewSession {
            SqlProjectionBox.createCriteria().list {
                projections {
                    sqlProjection 'width, height', ['width'], [INTEGER]
                }
            }
        }

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('selects 2 columns but 1 column aliases')

        when:
        SqlProjectionBox.withNewSession {
            SqlProjectionBox.createCriteria().list {
                projections {
                    sqlGroupProjection 'width', 'width', ['width'], [INTEGER, INTEGER]
                }
            }
        }

        then:
        e = thrown(IllegalArgumentException)
        e.message.contains('as many types as column aliases')
    }

    private static void saveBoxes() {
        SqlProjectionShelf top = new SqlProjectionShelf(label: 'Top').save()
        SqlProjectionShelf bottom = new SqlProjectionShelf(label: 'Bottom').save()
        LocalDateTime monday = LocalDateTime.of(2026, 9, 28, 9, 30)
        new SqlProjectionBox(label: 'A', width: 2, height: 7, shelf: top, packedAt: date(monday)).save()
        new SqlProjectionBox(label: 'A', width: 2, height: 8, shelf: top, packedAt: date(monday.plusHours(3))).save()
        new SqlProjectionBox(label: 'B', width: 2, height: 9, shelf: top, packedAt: date(monday.plusHours(5))).save()
        new SqlProjectionBox(label: 'C', width: 4, height: 9, shelf: bottom, packedAt: date(monday.plusDays(1))).save(flush: true)
    }

    private static Date date(LocalDateTime dateTime) {
        Date.from(dateTime.atZone(TimeZone.default.toZoneId()).toInstant())
    }
}

class SqlProjectionCapture implements StatementInspector {

    final List<String> statements = Collections.synchronizedList([])

    @Override
    String inspect(String sql) {
        statements << sql
        sql
    }
}

@Entity
class SqlProjectionShelf {

    String label
}

@Entity
class SqlProjectionBox {

    String label
    Integer width
    Integer height
    Date packedAt
    SqlProjectionShelf shelf
}
