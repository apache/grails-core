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
package org.apache.grails.data.graphql.core.querytail

import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.schema.GraphQLSchema
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.gorm.graphql.Schema
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The statements of each GraphQL operation counted under the operation, as a GORM datastore captures them while a
 * GraphQL built from its mapping executes the operation in a request. No reader resolves the request in this module,
 * so the page of a statement is its request line, with the operation after it.
 */
class QueryTailInstrumentationSpec extends Specification {

    static final String REQUEST = 'POST /?'

    @Shared
    @TempDir
    File tempDir

    @Shared
    File file

    @Shared
    HibernateDatastore datastore

    @Shared
    GraphQLSchema schema

    /** How many statements the capture held once each request was numbered, by the id of the request. */
    private final Map<String, Integer> firstOfRequest = [:]

    void setupSpec() {
        file = new File(tempDir, 'capture.log')
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([(Settings.SETTING_DB_CREATE): 'create-drop',
                (QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): file.path]),
                QueryTailAuthor, QueryTailBook)
        schema = new Schema(datastore.mappingContext).generate()
        QueryTailAuthor.withNewTransaction {
            ['Ada', 'Grace'].each { String name ->
                QueryTailAuthor author = new QueryTailAuthor(name: name)
                author.addToBooks(title: "${name} 1".toString())
                author.addToBooks(title: "${name} 2".toString())
                author.save(flush: true, failOnError: true)
            }
        }
    }

    void cleanupSpec() {
        datastore?.close()
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "the statements a named operation runs, loading associations as its fields are read too, count under the operation"() {
        given:
        GraphQL graphQL = QueryTailInstrumentation.install(GraphQL.newGraphQL(schema).build())
        String request = bindRequest()

        when: 'the books of each author are a collection loaded only as the field is read, in the session of the request'
        ExecutionResult result = QueryTailAuthor.withNewSession {
            graphQL.execute('query BooksWithAuthors { queryTailBookList(sort: "title") { title author { name books { title } } } }')
        }
        QueryTailAuthor.withTransaction { QueryTailAuthor.count() }
        List<QueryTailRecord> statements = statementsOf(request)
        List<QueryTailRecord> ofOperation = statements.findAll { it.page == "${REQUEST} (query BooksWithAuthors)" }

        then: 'the operation ran'
        result.errors.empty
        result.data.queryTailBookList*.title == ['Ada 1', 'Ada 2', 'Grace 1', 'Grace 2']
        result.data.queryTailBookList*.author*.books*.title*.sort() == [['Ada 1', 'Ada 2']] * 2 + [['Grace 1', 'Grace 2']] * 2

        and: 'the list it read and the collection of each author count under it'
        ofOperation.first().sql.contains('from query_tail_book')
        ofOperation.count { it.sql.contains('from query_tail_book') && it.sql.contains('author_id=?') } == 2

        and: 'the statement the request ran after it counts under the page of the request, which no reader resolved'
        statements.last().page == null
        statements.last().request == REQUEST
        statements.last().sql.startsWith('select count(')
        statements*.page.unique() == ["${REQUEST} (query BooksWithAuthors)", null]
    }

    void "an operation without a name is named by the fields it selects at the top, through its fragments, never by an alias or an argument"() {
        given:
        GraphQL graphQL = QueryTailInstrumentation.install(GraphQL.newGraphQL(schema).build())
        String request = bindRequest()

        when:
        ExecutionResult result = graphQL.execute('''{
            authors: queryTailAuthorCount
            ...Books
            ... on Query { queryTailAuthor(id: 1) { name } }
        }
        fragment Books on Query { queryTailBookList(max: 1) { title } queryTailAuthorCount }''')

        then:
        result.errors.empty
        statementsOf(request)*.page.unique() == ["${REQUEST} (query {queryTailAuthorCount, queryTailBookList, queryTailAuthor})"]
    }

    void "the operation a document holds several of is the one the request names: #operationName"() {
        given:
        GraphQL graphQL = QueryTailInstrumentation.install(GraphQL.newGraphQL(schema).build())
        String request = bindRequest()

        when:
        ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
                .query('query Authors { queryTailAuthorCount } query Books { queryTailBookCount }')
                .operationName(operationName)
                .build())

        then:
        result.errors.empty
        statementsOf(request)*.page.unique() == ["${REQUEST} (query ${operationName})".toString()]

        where:
        operationName << ['Authors', 'Books']
    }

    void "a mutation counts what it writes under the mutation, without the values it is given"() {
        given:
        GraphQL graphQL = QueryTailInstrumentation.install(GraphQL.newGraphQL(schema).build())
        String request = bindRequest()

        when:
        ExecutionResult result = graphQL.execute(
                'mutation AddAuthor { queryTailAuthorCreate(queryTailAuthor: {name: "example-secret"}) { id name } }')
        List<QueryTailRecord> statements = statementsOf(request)

        then:
        result.errors.empty
        result.data.queryTailAuthorCreate.name == 'example-secret'
        statements.any { it.sql.startsWith('insert into query_tail_author') }
        statements*.page.unique() == ["${REQUEST} (mutation AddAuthor)"]
        !file.text.contains('example-secret')

        cleanup:
        QueryTailAuthor.withNewTransaction { QueryTailAuthor.findByName('example-secret')?.delete(flush: true) }
    }

    void "an operation executed outside a request counts under its thread"() {
        given:
        GraphQL graphQL = QueryTailInstrumentation.install(GraphQL.newGraphQL(schema).build())
        int before = records().size()

        when:
        ExecutionResult result = graphQL.execute('query Authors { queryTailAuthorCount }')
        List<QueryTailRecord> statements = records().drop(before)

        then:
        result.errors.empty
        !statements.empty
        statements.every { it.page == "thread:${Thread.currentThread().name}" && it.requestId == null }
    }

    void "a GraphQL not given the instrumentation counts every operation under the page of its request"() {
        given:
        GraphQL graphQL = GraphQL.newGraphQL(schema).build()
        String request = bindRequest()

        when:
        ExecutionResult result = graphQL.execute('query Authors { queryTailAuthorCount }')

        then:
        result.errors.empty
        statementsOf(request)*.page.unique() == [null]
        statementsOf(request)*.request.unique() == [REQUEST]
    }

    void "the instrumentation a GraphQL has runs on beside the one installed"() {
        given:
        AtomicInteger operations = new AtomicInteger()
        GraphQL graphQL = QueryTailInstrumentation.install(GraphQL.newGraphQL(schema)
                .instrumentation(new CountingInstrumentation(operations))
                .build())
        String request = bindRequest()

        when:
        ExecutionResult result = graphQL.execute('query Authors { queryTailAuthorCount }')

        then:
        result.errors.empty
        operations.get() == 1
        statementsOf(request)*.page.unique() == ["${REQUEST} (query Authors)"]
    }

    /**
     * Binds a new request, and runs a statement in it, so that its id is known before the operation runs.
     *
     * @return the id the capture gave the request
     */
    private String bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest('POST', '/graphql')))
        QueryTailAuthor.withTransaction { QueryTailAuthor.executeQuery('select 1 from QueryTailAuthor where 1 = 0') }
        List<QueryTailRecord> captured = records()
        String request = captured.last().requestId
        // the statement that numbered the request is not one of the operation's
        firstOfRequest[request] = captured.size()
        request
    }

    /**
     * @return the statements a request ran after the one that numbered it
     */
    private List<QueryTailRecord> statementsOf(String request) {
        records().drop(firstOfRequest[request]).findAll { it.requestId == request }
    }

    private List<QueryTailRecord> records() {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }
}

/**
 * Counts the operations it sees begin, as an instrumentation an application gives its GraphQL does its own work.
 */
class CountingInstrumentation extends SimplePerformantInstrumentation {

    final AtomicInteger operations

    CountingInstrumentation(AtomicInteger operations) {
        this.operations = operations
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters,
            InstrumentationState state) {
        operations.incrementAndGet()
        SimpleInstrumentationContext.noOp()
    }
}

@Entity
class QueryTailAuthor {
    String name

    static hasMany = [books: QueryTailBook]

    static graphql = true
}

@Entity
class QueryTailBook {
    String title

    static belongsTo = [author: QueryTailAuthor]

    static graphql = true
}
