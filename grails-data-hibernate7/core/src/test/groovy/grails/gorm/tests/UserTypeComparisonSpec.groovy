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

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.UserType

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

/**
 * Comparison criteria on a property mapped with a custom {@link UserType}, through every
 * query API that builds a JPA criteria query, with HQL as the control.
 */
class UserTypeComparisonSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(UserTypeComparisonItem)
    }

    void setup() {
        save('low', 100, 1, 5, 2)
        save('mid', 200, 2, 5, 3)
        save('high', 300, 3, 10, 2)
        manager.session.flush()
        manager.session.clear()
    }

    void 'the values round-trip through the user types'() {
        when:
        UserTypeComparisonItem item = UserTypeComparisonItem.findByName('mid')

        then:
        item.amount == new UserTypeComparisonAmount(200)
        item.rank.level == 2
        item.grade == new UserTypeComparisonGrade(2)
    }

    void 'HQL compares the user type property with a parameter'() {
        expect:
        names(UserTypeComparisonItem.executeQuery(
                'from UserTypeComparisonItem i where i.amount > :amount',
                [amount: new UserTypeComparisonAmount(100)])) == ['high', 'mid']
    }

    void 'dynamic finder #finder compares the user type property'() {
        expect:
        names(UserTypeComparisonItem."findAllBy${finder}"(new UserTypeComparisonAmount(200))) == expected

        where:
        finder                    | expected
        'Amount'                  | ['mid']
        'AmountNotEqual'          | ['high', 'low']
        'AmountGreaterThan'       | ['high']
        'AmountGreaterThanEquals' | ['high', 'mid']
        'AmountLessThan'          | ['low']
        'AmountLessThanEquals'    | ['low', 'mid']
    }

    void 'criteria #operator compares the user type property'() {
        expect:
        names(UserTypeComparisonItem.createCriteria().list {
            "${operator}"('amount', new UserTypeComparisonAmount(200))
        }) == expected

        where:
        operator | expected
        'eq'     | ['mid']
        'ne'     | ['high', 'low']
        'gt'     | ['high']
        'ge'     | ['high', 'mid']
        'lt'     | ['low']
        'le'     | ['low', 'mid']
    }

    void 'where query #description compares the user type property'() {
        given:
        UserTypeComparisonAmount amount = new UserTypeComparisonAmount(200)

        expect:
        names(query(amount).list()) == expected

        where:
        description | query                                                                          | expected
        '=='        | { UserTypeComparisonAmount a -> UserTypeComparisonItem.where { amount == a } } | ['mid']
        '!='        | { UserTypeComparisonAmount a -> UserTypeComparisonItem.where { amount != a } } | ['high', 'low']
        '>'         | { UserTypeComparisonAmount a -> UserTypeComparisonItem.where { amount > a } }  | ['high']
        '>='        | { UserTypeComparisonAmount a -> UserTypeComparisonItem.where { amount >= a } } | ['high', 'mid']
        '<'         | { UserTypeComparisonAmount a -> UserTypeComparisonItem.where { amount < a } }  | ['low']
        '<='        | { UserTypeComparisonAmount a -> UserTypeComparisonItem.where { amount <= a } } | ['low', 'mid']
    }

    void 'detached criteria count compares the user type property'() {
        expect:
        new DetachedCriteria<>(UserTypeComparisonItem).build {
            gt('amount', new UserTypeComparisonAmount(100))
        }.count() == 2
    }

    void 'inList and between compare the user type property'() {
        expect:
        names(UserTypeComparisonItem.createCriteria().list {
            inList('amount', [new UserTypeComparisonAmount(100), new UserTypeComparisonAmount(300)])
        }) == ['high', 'low']
        names(UserTypeComparisonItem.createCriteria().list {
            between('amount', new UserTypeComparisonAmount(150), new UserTypeComparisonAmount(250))
        }) == ['mid']
    }

    void 'criteria #operator compares a user type property whose class is neither Comparable nor Serializable'() {
        expect:
        names(UserTypeComparisonItem.createCriteria().list {
            "${operator}"('rank', new UserTypeComparisonRank(2))
        }) == expected

        where:
        operator | expected
        'eq'     | ['mid']
        'ne'     | ['high', 'low']
        'gt'     | ['high']
        'ge'     | ['high', 'mid']
        'lt'     | ['low']
        'le'     | ['low', 'mid']
    }

    void 'criteria #operator compares a user type property whose class is Serializable but not Comparable'() {
        expect:
        names(UserTypeComparisonItem.createCriteria().list {
            "${operator}"('grade', new UserTypeComparisonGrade(2))
        }) == expected

        where:
        operator | expected
        'eq'     | ['mid']
        'ne'     | ['high', 'low']
        'gt'     | ['high']
        'ge'     | ['high', 'mid']
        'lt'     | ['low']
        'le'     | ['low', 'mid']
    }

    void 'dynamic finder #finder compares a user type property whose class is Serializable but not Comparable'() {
        expect:
        names(UserTypeComparisonItem."findAllBy${finder}"(new UserTypeComparisonGrade(2))) == expected

        where:
        finder                   | expected
        'Grade'                  | ['mid']
        'GradeNotEqual'          | ['high', 'low']
        'GradeGreaterThan'       | ['high']
        'GradeGreaterThanEquals' | ['high', 'mid']
        'GradeLessThan'          | ['low']
        'GradeLessThanEquals'    | ['low', 'mid']
    }

    void 'where query #description compares a user type property whose class is Serializable but not Comparable'() {
        given:
        UserTypeComparisonGrade grade = new UserTypeComparisonGrade(2)

        expect:
        names(query(grade).list()) == expected

        where:
        description | query                                                                        | expected
        '=='        | { UserTypeComparisonGrade g -> UserTypeComparisonItem.where { grade == g } } | ['mid']
        '!='        | { UserTypeComparisonGrade g -> UserTypeComparisonItem.where { grade != g } } | ['high', 'low']
        '>'         | { UserTypeComparisonGrade g -> UserTypeComparisonItem.where { grade > g } }  | ['high']
        '>='        | { UserTypeComparisonGrade g -> UserTypeComparisonItem.where { grade >= g } } | ['high', 'mid']
        '<'         | { UserTypeComparisonGrade g -> UserTypeComparisonItem.where { grade < g } }  | ['low']
        '<='        | { UserTypeComparisonGrade g -> UserTypeComparisonItem.where { grade <= g } } | ['low', 'mid']
    }

    void 'criteria get compares a user type property whose class is not Comparable'() {
        expect:
        UserTypeComparisonItem.createCriteria().get {
            gt('grade', new UserTypeComparisonGrade(2))
        }.name == 'high'
    }

    void 'criteria scroll compares a user type property whose class is not Comparable'() {
        when:
        def results = UserTypeComparisonItem.createCriteria().scroll {
            gt('grade', new UserTypeComparisonGrade(1))
            order('name', 'asc')
        }

        then:
        results.next()
        results.get().name == 'high'
        results.next()
        results.get().name == 'mid'
        !results.next()

        cleanup:
        results?.close()
    }

    void 'a count with projections compares a user type property whose class is not Comparable'() {
        expect:
        new DetachedCriteria<>(UserTypeComparisonItem).build {
            gt('grade', new UserTypeComparisonGrade(1))
            projections {
                groupProperty('name')
            }
        }.count() == 2
    }

    void 'a query and its subquery each compare a user type property whose class is not Comparable'() {
        given:
        DetachedCriteria<UserTypeComparisonItem> higherGrades = new DetachedCriteria<>(UserTypeComparisonItem).build {
            gt('grade', new UserTypeComparisonGrade(1))
            projections {
                property('id')
            }
        }

        expect:
        names(new DetachedCriteria<>(UserTypeComparisonItem).build {
            lt('rank', new UserTypeComparisonRank(3))
            inList('id', higherGrades)
        }.list()) == ['mid']
    }

    void 'a comparison with property arithmetic still compares against the expression'() {
        given:
        Closure criteria = { gt('quantity', reorderLevel * 2) }

        expect:
        names(UserTypeComparisonItem.where(criteria).list()) == ['high', 'low']
    }

    private static void save(String name, long cents, int level, int quantity, int reorderLevel) {
        new UserTypeComparisonItem(
                name: name,
                amount: new UserTypeComparisonAmount(cents),
                rank: new UserTypeComparisonRank(level),
                grade: new UserTypeComparisonGrade(level),
                quantity: quantity,
                reorderLevel: reorderLevel
        ).save(failOnError: true)
    }

    private static List<String> names(List<UserTypeComparisonItem> items) {
        items*.name.sort()
    }
}

@Entity
class UserTypeComparisonItem {

    String name
    UserTypeComparisonAmount amount
    UserTypeComparisonRank rank
    UserTypeComparisonGrade grade
    Integer quantity
    Integer reorderLevel

    static mapping = {
        amount type: UserTypeComparisonAmountType
        rank type: UserTypeComparisonRankType
        grade type: UserTypeComparisonGradeType
    }
}

@CompileStatic
@EqualsAndHashCode
class UserTypeComparisonAmount implements Serializable, Comparable<UserTypeComparisonAmount> {

    final long cents

    UserTypeComparisonAmount(long cents) {
        this.cents = cents
    }

    @Override
    int compareTo(UserTypeComparisonAmount other) {
        Long.compare(cents, other.cents)
    }
}

@CompileStatic
class UserTypeComparisonAmountType implements UserType<UserTypeComparisonAmount> {

    @Override
    int getSqlType() {
        Types.BIGINT
    }

    @Override
    Class<UserTypeComparisonAmount> returnedClass() {
        UserTypeComparisonAmount
    }

    @Override
    UserTypeComparisonAmount nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        long cents = rs.getLong(position)
        rs.wasNull() ? null : new UserTypeComparisonAmount(cents)
    }

    @Override
    void nullSafeSet(PreparedStatement st, UserTypeComparisonAmount value, int position, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(position, Types.BIGINT)
        }
        else {
            st.setLong(position, value.cents)
        }
    }

    @Override
    UserTypeComparisonAmount deepCopy(UserTypeComparisonAmount value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }
}

@CompileStatic
@EqualsAndHashCode
class UserTypeComparisonRank {

    final int level

    UserTypeComparisonRank(int level) {
        this.level = level
    }
}

@CompileStatic
@EqualsAndHashCode
class UserTypeComparisonGrade implements Serializable {

    final int level

    UserTypeComparisonGrade(int level) {
        this.level = level
    }
}

@CompileStatic
abstract class UserTypeComparisonIntegerType<T> implements UserType<T> {

    protected abstract T fromLevel(int level)

    protected abstract int toLevel(T value)

    @Override
    int getSqlType() {
        Types.INTEGER
    }

    @Override
    T nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        int level = rs.getInt(position)
        rs.wasNull() ? null : fromLevel(level)
    }

    @Override
    void nullSafeSet(PreparedStatement st, T value, int position, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(position, Types.INTEGER)
        }
        else {
            st.setInt(position, toLevel(value))
        }
    }

    @Override
    T deepCopy(T value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }
}

@CompileStatic
class UserTypeComparisonRankType extends UserTypeComparisonIntegerType<UserTypeComparisonRank> {

    @Override
    Class<UserTypeComparisonRank> returnedClass() {
        UserTypeComparisonRank
    }

    @Override
    protected UserTypeComparisonRank fromLevel(int level) {
        new UserTypeComparisonRank(level)
    }

    @Override
    protected int toLevel(UserTypeComparisonRank value) {
        value.level
    }
}

@CompileStatic
class UserTypeComparisonGradeType extends UserTypeComparisonIntegerType<UserTypeComparisonGrade> {

    @Override
    Class<UserTypeComparisonGrade> returnedClass() {
        UserTypeComparisonGrade
    }

    @Override
    protected UserTypeComparisonGrade fromLevel(int level) {
        new UserTypeComparisonGrade(level)
    }

    @Override
    protected int toLevel(UserTypeComparisonGrade value) {
        value.level
    }
}
