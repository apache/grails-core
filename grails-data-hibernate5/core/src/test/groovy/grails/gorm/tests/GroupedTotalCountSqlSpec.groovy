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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The total of a page of a criteria grouped by one property is counted by the database as the distinct values of the
 * property, and one grouped by several as the rows of the grouped query, and not by loading the groups to count them.
 */
@Issue('https://github.com/apache/grails-core/issues/14431')
class GroupedTotalCountSqlSpec extends Specification {

    @Shared List<String> statements = Collections.synchronizedList(new ArrayList<String>())

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': new GroupedSqlCapture(statements)
            ),
            GroupedSqlOwner, GroupedSqlItem
    )

    @Rollback
    void "the total of a page grouped by one property is counted by the database"() {
        given:
        ['a', 'a', 'b', 'c', null].eachWithIndex { String city, int i ->
            new GroupedSqlItem(name: "item$i", city: city).save(flush: true, failOnError: true)
        }
        GroupedSqlItem.withSession { it.clear() }

        when:
        def page = GroupedSqlItem.createCriteria().list(max: 2) {
            projections {
                groupProperty('city')
            }
            order('city')
        }
        statements.clear()
        int total = page.totalCount

        then: 'a, b, c and the group of the nulls, from one query that returns one number'
        total == 4
        statements.size() == 1
        statements[0].toLowerCase().startsWith('select count(')
    }

    @Rollback
    void "the total of a page grouped by two properties is counted by the database"() {
        given: 'a, x twice; a, y; b, x; and two rows with a null city, one of them with a null name too'
        [['a', 'x'], ['a', 'x'], ['a', 'y'], ['b', 'x'], [null, 'x'], [null, null], [null, null]].eachWithIndex { List pair, int i ->
            new GroupedSqlItem(name: pair[1], city: pair[0]).save(flush: true, failOnError: true)
        }
        GroupedSqlItem.withSession { it.clear() }

        when:
        def page = GroupedSqlItem.createCriteria().list(max: 2) {
            projections {
                groupProperty('city')
                groupProperty('name')
            }
            order('city')
        }
        statements.clear()
        int total = page.totalCount

        then: '(a, x), (a, y), (b, x), (null, x) and (null, null): five groups, from one query that returns one number'
        total == 5
        statements.size() == 1
        statements[0].toLowerCase().startsWith('select count(')
    }

    @Rollback
    void "the total of a grouped page that has a condition and a count projection is counted by the database"() {
        given:
        [['a', 'x'], ['a', 'x'], ['a', 'y'], ['b', 'x'], ['c', 'z']].each { List pair ->
            new GroupedSqlItem(name: pair[1], city: pair[0]).save(flush: true, failOnError: true)
        }
        GroupedSqlItem.withSession { it.clear() }

        when:
        def page = GroupedSqlItem.createCriteria().list(max: 1) {
            ne('city', 'c')
            projections {
                groupProperty('city')
                groupProperty('name')
                rowCount()
            }
        }
        statements.clear()
        int total = page.totalCount

        then: 'the condition is part of the count'
        total == 3
        statements.size() == 1
        statements[0].toLowerCase().startsWith('select count(')
    }

    @Rollback
    @Unroll
    void "the total of a page grouped as #shape equals the number of groups"() {
        given:
        GroupedSqlOwner ann = new GroupedSqlOwner(label: 'ann').save(failOnError: true)
        GroupedSqlOwner bob = new GroupedSqlOwner(label: 'bob').save(failOnError: true)
        [['a', 'x', ann, 1], ['a', 'x', bob, 2], ['a', 'y', ann, 3], ['b', 'x', ann, 4], [null, 'x', bob, 5], [null, null, bob, 6],
         [null, null, bob, 7]].each { List row ->
            // the address follows the name: the street and the zip of a row without a name are both null
            GroupedSqlItem item = new GroupedSqlItem(city: row[0], name: row[1], owner: row[2], amount: row[3])
            if (row[1] == 'x' && row[0] == 'a') {
                item.address = new GroupedSqlAddress(street: 'main', zip: '1')
            }
            else if (row[1] == 'y') {
                item.address = new GroupedSqlAddress(street: 'main', zip: null)
            }
            else if (row[1] == 'x') {
                item.address = new GroupedSqlAddress(street: null, zip: '2')
            }
            item.save(failOnError: true)
        }
        GroupedSqlItem.withSession { it.flush(); it.clear() }

        when:
        List all = GroupedSqlItem.createCriteria().list(closure)
        def page = GroupedSqlItem.createCriteria().list(max: 1, offset: 1, closure)
        statements.clear()
        int total = page.totalCount

        then: 'the database counts the groups, in one query that returns one number'
        total == all.size()
        total == expected
        statements.size() == 1
        statements.every { it.toLowerCase().startsWith('select count(') }

        where:
        shape                                     | expected | closure
        'city and name'                           | 5        | { -> projections { groupProperty('city'); groupProperty('name') } }
        'name and city'                           | 5        | { -> projections { groupProperty('name'); groupProperty('city') } }
        'city, name and owner'                    | 6        | { -> projections { groupProperty('city'); groupProperty('name'); groupProperty('owner') } }
        'city and an aggregate'                   | 3        | { -> projections { groupProperty('city'); max('amount') } }
        'the property of an association'          | 2        | { ->
            createAlias('owner', 'o')
            projections { groupProperty('o.label') }
        }
        'a property and one of an association'    | 4        | { ->
            createAlias('owner', 'o')
            projections { groupProperty('city'); groupProperty('o.label') }
        }
        'two properties under a condition'        | 2        | { ->
            ne('city', 'b')
            projections { groupProperty('city'); groupProperty('name') }
        }
        'two properties with no rows'             | 0        | { ->
            eq('city', 'none')
            projections { groupProperty('city'); groupProperty('name') }
        }
        'an embedded value'                       | 4        | { -> projections { groupProperty('address') } }
        'an embedded value and a property'        | 5        | { -> projections { groupProperty('address'); groupProperty('city') } }
        'an embedded value under a condition'     | 3        | { ->
            isNotNull('city')
            projections { groupProperty('address') }
        }
        'two properties that are all null'        | 1        | { ->
            isNull('city')
            isNull('name')
            projections { groupProperty('city'); groupProperty('name') }
        }
    }

    static class GroupedSqlCapture implements StatementInspector {
        private final List<String> statements

        GroupedSqlCapture(List<String> statements) {
            this.statements = statements
        }

        @Override
        String inspect(String sql) {
            statements.add(sql)
            return sql
        }
    }
}

class GroupedSqlAddress {
    String street
    String zip

    static constraints = {
        street nullable: true
        zip nullable: true
    }
}

@Entity
class GroupedSqlOwner {
    String label
}

@Entity
class GroupedSqlItem {
    String name
    String city
    GroupedSqlOwner owner
    Integer amount
    GroupedSqlAddress address

    static embedded = ['address']

    static constraints = {
        name nullable: true
        owner nullable: true
        address nullable: true
        amount nullable: true
        city nullable: true
    }
}
