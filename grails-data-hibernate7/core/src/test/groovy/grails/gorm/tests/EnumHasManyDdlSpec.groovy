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
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.engine.spi.SessionImplementor
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.sql.ResultSet

/**
 * Reproduces https://github.com/apache/grails-core/issues/16051
 *
 * A domain with a `hasMany` collection whose related type is an enum (a
 * Set of a basic/enum type, not an entity) produces broken join table DDL:
 * the element column is bound against the owning entity's table instead of
 * the join table, and its name is derived from the enum's fully-qualified
 * class name instead of its simple name.
 */
@Rollback
class EnumHasManyDdlSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore =
        new HibernateDatastore(SurveyResponse, OrdinalSurveyResponse, NamedColumnSurveyResponse, NonNullableSurveyResponse)

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "join table for a hasMany of enum is created with the element column"() {
        expect: "the join table has exactly the owner FK column and the element column, named from the enum's simple name"
        columnNamesFor('SURVEY_RESPONSE_ANSWERS') == ['survey_response_id', 'survey_answer'] as Set
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "the owner table does not get a spurious column for the hasMany enum element"() {
        expect: "no answer-related column leaked onto survey_response itself"
        !columnNamesFor('SURVEY_RESPONSE').any { it.contains('answer') }
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "a hasMany of enum can actually be saved and reloaded"() {
        given:
        def response = new SurveyResponse(respondent: "Alice")
        response.addToAnswers(SurveyAnswer.MAYBE)
        response.addToAnswers(SurveyAnswer.DONT_KNOW)

        when:
        response.save(flush: true)
        response.discard()
        def reloaded = SurveyResponse.get(response.id)

        then:
        reloaded.answers.sort() == [SurveyAnswer.MAYBE, SurveyAnswer.DONT_KNOW].sort()
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "join table for a hasMany of enum with enumType ordinal has the element column"() {
        expect:
        columnNamesFor('ORDINAL_SURVEY_RESPONSE_ANSWERS') == ['ordinal_survey_response_id', 'survey_answer'] as Set
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "a hasMany of enum with enumType ordinal stores the ordinal, not the name"() {
        given:
        def response = new OrdinalSurveyResponse(respondent: "Bob")
        response.addToAnswers(SurveyAnswer.MAYBE)
        response.save(flush: true)

        expect: "the raw stored element value is the enum's ordinal, not its name"
        rawElementValuesFor('ORDINAL_SURVEY_RESPONSE_ANSWERS', 'survey_answer') ==
                [String.valueOf(SurveyAnswer.MAYBE.ordinal())] as Set

        when:
        response.discard()
        def reloaded = OrdinalSurveyResponse.get(response.id)

        then:
        reloaded.answers == [SurveyAnswer.MAYBE] as Set
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "a hasMany of enum with the default enumType stores the name, not the ordinal"() {
        given:
        def response = new SurveyResponse(respondent: "Dave")
        response.addToAnswers(SurveyAnswer.MAYBE)
        response.save(flush: true)

        expect: "the raw stored element value is the enum's name"
        rawElementValuesFor('SURVEY_RESPONSE_ANSWERS', 'survey_answer') == [SurveyAnswer.MAYBE.name()] as Set
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "join table for a hasMany of enum with an explicit joinTable column name uses it verbatim"() {
        expect: "the element column uses the explicitly configured name instead of the derived one"
        columnNamesFor('NAMED_COLUMN_SURVEY_RESPONSE_ANSWERS') ==
                ['named_column_survey_response_id', 'chosen_answer'] as Set
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "a hasMany of enum with an explicit joinTable column name can be saved and reloaded"() {
        given:
        def response = new NamedColumnSurveyResponse(respondent: "Carol")
        response.addToAnswers(SurveyAnswer.MAYBE_NOT)

        when:
        response.save(flush: true)
        response.discard()
        def reloaded = NamedColumnSurveyResponse.get(response.id)

        then:
        reloaded.answers == [SurveyAnswer.MAYBE_NOT] as Set
    }

    @Issue("https://github.com/apache/grails-core/issues/16051")
    void "the hasMany enum element column stays nullable even when nullable false is declared, matching the non-enum sibling path"() {
        expect: "nullable: false on a hasMany-of-enum does not reach the element column, same as a hasMany-of-String would"
        isNullableColumn('NON_NULLABLE_SURVEY_RESPONSE_ANSWERS', 'survey_answer')
    }

    private Set<String> columnNamesFor(String tableName) {
        SessionImplementor sessionImplementor = (SessionImplementor) datastore.sessionFactory.currentSession
        sessionImplementor.doReturningWork { connection ->
            Set<String> columnNames = []
            try (def statement = connection.prepareStatement(
                    "select column_name from information_schema.columns where table_name = ?")) {
                statement.setString(1, tableName)
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        columnNames << resultSet.getString('column_name').toLowerCase()
                    }
                }
            }
            columnNames
        }
    }

    private Set<String> rawElementValuesFor(String tableName, String columnName) {
        SessionImplementor sessionImplementor = (SessionImplementor) datastore.sessionFactory.currentSession
        sessionImplementor.doReturningWork { connection ->
            Set<String> values = []
            try (def statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(
                        "select ${columnName} from ${tableName}".toString())) {
                    while (resultSet.next()) {
                        values << resultSet.getString(1)
                    }
                }
            }
            values
        }
    }

    private boolean isNullableColumn(String tableName, String columnName) {
        SessionImplementor sessionImplementor = (SessionImplementor) datastore.sessionFactory.currentSession
        sessionImplementor.doReturningWork { connection ->
            try (def statement = connection.prepareStatement(
                    "select is_nullable from information_schema.columns " +
                    "where table_name = ? and column_name = ?")) {
                statement.setString(1, tableName)
                statement.setString(2, columnName.toUpperCase())
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next()
                    'YES'.equalsIgnoreCase(resultSet.getString('is_nullable'))
                }
            }
        }
    }
}

@Entity
class SurveyResponse {
    String respondent
    Set<SurveyAnswer> answers

    static hasMany = [answers: SurveyAnswer]

    static constraints = {
        respondent blank: false
    }
}

@Entity
class OrdinalSurveyResponse {
    String respondent
    Set<SurveyAnswer> answers

    static hasMany = [answers: SurveyAnswer]

    static mapping = {
        answers enumType: 'ordinal'
    }

    static constraints = {
        respondent blank: false
    }
}

@Entity
class NamedColumnSurveyResponse {
    String respondent
    Set<SurveyAnswer> answers

    static hasMany = [answers: SurveyAnswer]

    static mapping = {
        answers joinTable: [column: 'chosen_answer']
    }

    static constraints = {
        respondent blank: false
    }
}

@Entity
class NonNullableSurveyResponse {
    String respondent
    Set<SurveyAnswer> answers

    static hasMany = [answers: SurveyAnswer]

    static constraints = {
        respondent blank: false
        answers nullable: false
    }
}

enum SurveyAnswer {
    FOR_SURE, MAYBE, MAYBE_NOT, DONT_KNOW
}
