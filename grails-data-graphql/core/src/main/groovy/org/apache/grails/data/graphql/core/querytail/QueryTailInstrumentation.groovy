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

import groovy.transform.CompileStatic

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.ExecutionContext
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet

import org.apache.grails.datamapping.core.querytail.QueryTailRequestAttribution

/**
 * Counts the statements each GraphQL operation runs under the operation, for the {@code query-tail} capture. A
 * GraphQL endpoint serves every operation on one page, as the GORM GraphQL plugin serves them on
 * {@code graphql/index}; with this instrumentation the statements a request runs while an operation executes are
 * counted under the page with the operation after it: {@code graphql/index (query BookList)}. An operation is named by
 * its type and the name its document gives it, or, when the document gives it none, by its type and the fields it
 * selects at the top, which the schema defines, as {@code query {bookList, authorCount}}; an argument, an alias or a
 * variable of the operation is never part of it. Only an operation that is valid against the schema executes, so
 * every field named is one of the schema.
 *
 * <p>The operation counts from the moment it starts executing until it completes, on whichever thread it completes,
 * so that every statement its data fetchers run on the thread of the request counts under it, including those that
 * load an association as its fields are read. A statement run on another thread, as a fetcher that returns its data
 * later may run one, counts under that thread, as any work outside a request does.</p>
 *
 * <p>The GORM GraphQL plugin adds this instrumentation to the GraphQL it builds when capture is enabled with
 * {@code grails.gorm.queryTail.enabled}; a GraphQL an application builds itself can be given it with
 * {@link #install(GraphQL)}.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailInstrumentation extends SimplePerformantInstrumentation {

    /**
     * Adds the instrumentation to a GraphQL, after the instrumentation it has.
     *
     * @param graphQL a GraphQL
     * @return a GraphQL that is the one given with this instrumentation added
     */
    static GraphQL install(GraphQL graphQL) {
        Instrumentation existing = graphQL.instrumentation
        Instrumentation instrumentation = existing == null ? new QueryTailInstrumentation() :
                new ChainedInstrumentation(existing, new QueryTailInstrumentation())
        graphQL.transform { GraphQL.Builder builder -> builder.instrumentation(instrumentation) }
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters,
            InstrumentationState state) {
        ExecutionContext context = parameters.executionContext
        Runnable end = QueryTailRequestAttribution.beginOperation(operationOf(context.operationDefinition, context.fragmentsByName))
        SimpleInstrumentationContext.<ExecutionResult> whenCompleted { ExecutionResult result, Throwable failure -> end.run() }
    }

    /**
     * Names an operation: by its type and its name, or by its type and the fields it selects at the top when it has
     * no name, each once, in the order the document first selects them, through the fragments it spreads.
     *
     * @param operation the operation
     * @param fragments the fragments of its document, by name
     * @return the name of the operation, as {@code query BookList} or {@code query {bookList, authorCount}}
     */
    private static String operationOf(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        String type = operation.operation.name().toLowerCase(Locale.ROOT)
        if (operation.name) {
            return "${type} ${operation.name}".toString()
        }
        Set<String> fields = new LinkedHashSet<>()
        collectFields(operation.selectionSet, fragments, fields, new HashSet<String>())
        "${type} {${fields.join(', ')}}".toString()
    }

    private static void collectFields(SelectionSet selectionSet, Map<String, FragmentDefinition> fragments, Set<String> fields,
            Set<String> spread) {
        if (selectionSet == null) {
            return
        }
        for (Selection selection in selectionSet.selections) {
            if (selection instanceof Field) {
                fields.add(((Field) selection).name)
            }
            else if (selection instanceof InlineFragment) {
                collectFields(((InlineFragment) selection).selectionSet, fragments, fields, spread)
            }
            else if (selection instanceof FragmentSpread) {
                String name = ((FragmentSpread) selection).name
                // a valid document spreads no fragment within itself, but one spread twice adds nothing the second time
                if (spread.add(name)) {
                    collectFields(fragments?.get(name)?.selectionSet, fragments, fields, spread)
                }
            }
        }
    }
}
