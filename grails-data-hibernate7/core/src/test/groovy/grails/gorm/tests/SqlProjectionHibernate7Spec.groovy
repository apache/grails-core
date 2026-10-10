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

import java.sql.Time
import java.time.Duration
import java.time.LocalDateTime

import org.hibernate.type.BasicTypeRegistry
import org.hibernate.type.StandardBasicTypes
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared

import grails.gorm.annotation.Entity

@Testcontainers
@Requires({ isDockerAvailable() })
class SqlProjectionHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = new PostgreSQLContainer('postgres:16')
    @Shared mysql = new MySQLContainer('mysql:8.0')
    @Shared mariadb = new MariaDBContainer('mariadb:10.11')
    @Shared oracle = new OracleContainer('gvenzl/oracle-free:slim-faststart')
            .withStartupTimeout(Duration.ofMinutes(3))

    void setupSpec() {
        manager.registerDomainClasses(SqlProjectionDbBox, SqlProjectionDbShelf)
    }

    void "sqlProjection and sqlGroupProjection work with #db"() {
        given:
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'            : container?.jdbcUrl ?: 'jdbc:h2:mem:sqlProjectionDB;LOCK_TIMEOUT=10000',
                'dataSource.driverClassName': container?.driverClassName ?: 'org.h2.Driver',
                'dataSource.username'       : container?.username ?: 'sa',
                'dataSource.password'       : container?.password ?: '',
                'dataSource.dbCreate'       : 'create-drop',
                'hibernate.show_sql'        : 'true',
        ]
        manager.setup(this.class)

        SqlProjectionDbShelf top = new SqlProjectionDbShelf(label: 'Top').save()
        SqlProjectionDbShelf bottom = new SqlProjectionDbShelf(label: 'Bottom').save()
        LocalDateTime monday = LocalDateTime.of(2026, 9, 28, 9, 30)
        new SqlProjectionDbBox(label: 'A', width: 2, height: 7, shelf: top, packedAt: date(monday)).save()
        new SqlProjectionDbBox(label: 'A', width: 2, height: 8, shelf: top, packedAt: date(monday.plusHours(3))).save()
        new SqlProjectionDbBox(label: 'B', width: 2, height: 9, shelf: top, packedAt: date(monday.plusHours(5))).save()
        new SqlProjectionDbBox(label: 'C', width: 4, height: 9, shelf: bottom, packedAt: date(monday.plusDays(1))).save(flush: true)
        manager.session.clear()
        BasicTypeRegistry types = manager.hibernateDatastore.sessionFactory.typeConfiguration.basicTypeRegistry

        expect: 'several values, one column each'
        SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlProjection '(2 * (width + height)) as perimeter, (width * height) as area', ['perimeter', 'area'], [INTEGER, INTEGER]
            }
            order('height')
        }*.toList() == [[18, 14], [20, 16], [22, 18], [26, 36]]

        and: 'a single aggregated value'
        SqlProjectionDbBox.createCriteria().get {
            projections {
                sqlProjection 'sum(width * height) as totalArea', 'totalArea', INTEGER
            }
        } == 84

        and: 'grouped by the group by clause'
        SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width, sum(height) as combinedHeightsForThisWidth', 'width',
                        ['width', 'combinedHeightsForThisWidth'], [INTEGER, INTEGER]
            }
            order('width')
        }*.toList() == [[2, 24], [4, 9]]

        and: 'grouped by a column alias and ordered by another'
        SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width * 10 as scaledWidth, sum(height) as total', 'scaledWidth', ['scaledWidth', 'total'], [INTEGER, INTEGER]
            }
            order('total', 'desc')
        }*.toList() == [[20, 24], [40, 9]]

        and: 'grouped by a quoted column alias'
        SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width as "box width", sum(height) as [total height]', '"box width"',
                        ['box width', 'total height'], [INTEGER, INTEGER]
            }
            order('total height')
        }*.toList() == [[4, 9], [2, 24]]

        and: 'combined with the other projections'
        SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width as boxWidth', 'width', ['boxWidth'], [INTEGER]
                rowCount()
                sum('height')
            }
            order('boxWidth')
        }*.toList() == [[2, 3L, 24L], [4, 1L, 9L]]

        and: '{alias} picks the queried entity when a join adds another label column'
        SqlProjectionDbBox.createCriteria().list {
            shelf {
                eq('label', 'Top')
            }
            projections {
                sqlGroupProjection 'upper({alias}.label) as boxLabel', 'upper({alias}.label)', ['boxLabel'], [STRING]
                rowCount()
            }
            order('boxLabel')
        }*.toList() == [['A', 2L], ['B', 1L]]

        and: 'a declared Hibernate type tells how the value is read'
        List typed = SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlProjection "cast({alias}.packed_at as date) as packedOn, case when {alias}.height > 8 then 'Y' else 'N' end as tall",
                        ['packedOn', 'tall'], [types.resolve(StandardBasicTypes.DATE), types.resolve(StandardBasicTypes.YES_NO)]
            }
            order('height')
        }
        typed.every { it[0] instanceof java.sql.Date }
        typed*.getAt(1) == [false, false, true, true]

        and: 'the type constants of the DSL'
        List constants = SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlGroupProjection 'width, avg(height) as averageHeight, count(*) as boxes, min(case when height > 8 then 1 else 0 end) as allTall',
                        'width', ['width', 'averageHeight', 'boxes', 'allTall'], [INTEGER, BIG_DECIMAL, LONG, BOOLEAN]
            }
            order('width')
        }
        constants*.getAt(1).every { it instanceof BigDecimal }
        constants*.getAt(1) == [8, 9]
        constants*.getAt(2) == [3L, 1L]
        constants*.getAt(3) == [false, true]

        and: 'a paged list counts the total'
        SqlProjectionDbBox.createCriteria().list(max: 2) {
            projections {
                sqlProjection 'width as boxWidth', 'boxWidth', INTEGER
            }
            order('height')
        }.totalCount == 4

        and: 'a time, which Oracle has no type for'
        db == 'Oracle' || SqlProjectionDbBox.createCriteria().list {
            projections {
                sqlProjection 'cast({alias}.packed_at as time) as packedTime', 'packedTime', TIME
            }
            order('height')
        }.every { it instanceof Time }

        where:
        db         | container
        'H2'       | null
        'Postgres' | postgres
        'MySQL'    | mysql
        'MariaDB'  | mariadb
        'Oracle'   | oracle
    }

    private static Date date(LocalDateTime dateTime) {
        Date.from(dateTime.atZone(TimeZone.default.toZoneId()).toInstant())
    }
}

@Entity
class SqlProjectionDbShelf {

    String label
    static mapping = {
        id(generator: 'identity')
    }
}

@Entity
class SqlProjectionDbBox {

    String label
    Integer width
    Integer height
    Date packedAt
    SqlProjectionDbShelf shelf
    static mapping = {
        id(generator: 'identity')
    }
}
