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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.hibernate.metamodel.model.domain.ReturnableType
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor
import org.hibernate.query.sqm.produce.function.FunctionArgumentTypeResolver
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers
import org.hibernate.sql.ast.SqlAstTranslator
import org.hibernate.sql.ast.spi.SqlAppender
import org.hibernate.sql.ast.tree.SqlAstNode
import org.hibernate.sql.ast.tree.expression.ColumnReference
import org.hibernate.sql.ast.tree.expression.Expression
import org.hibernate.sql.ast.tree.expression.Literal
import org.hibernate.sql.ast.tree.expression.SqlTuple
import org.hibernate.type.StandardBasicTypes
import org.hibernate.type.spi.TypeConfiguration

/**
 * Renders a {@link SqlRestriction} as a predicate, in parentheses. The arguments are the SQL condition as a literal,
 * a column of the queried entity whose table alias replaces {@code {alias}} if the SQL contains it, and the value of
 * each {@code ?} placeholder.
 *
 * @since 8.0.0
 */
@CompileStatic
class GrailsSqlRestrictionFunction extends AbstractSqmSelfRenderingFunctionDescriptor {

    public static final String NAME = 'grails_sql_restriction'

    @PackageScope
    static final String ALIAS_PLACEHOLDER = '{alias}'

    GrailsSqlRestrictionFunction(TypeConfiguration typeConfiguration) {
        super(
                NAME,
                StandardArgumentsValidators.min(1),
                StandardFunctionReturnTypeResolvers.invariant(
                        typeConfiguration.basicTypeRegistry.resolve(StandardBasicTypes.BOOLEAN)),
                (FunctionArgumentTypeResolver) null)
    }

    @Override
    boolean isPredicate() {
        return true
    }

    @Override
    void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        SqlAstNode first = arguments.get(0)
        if (!(first instanceof Literal) || !(((Literal) first).literalValue instanceof String)) {
            throw new IllegalArgumentException("The first argument of ${NAME} must be the SQL as a literal".toString())
        }
        String sql = (String) ((Literal) first).literalValue
        int firstValue = 1
        if (sql.contains(ALIAS_PLACEHOLDER)) {
            sql = sql.replace(ALIAS_PLACEHOLDER, tableAlias(arguments.get(1)))
            firstValue = 2
        }
        List<Integer> placeholders = SqlRestriction.placeholderIndexes(sql)
        if (placeholders.size() != arguments.size() - firstValue) {
            throw new IllegalArgumentException(
                    "The SQL restriction has ${placeholders.size()} ? placeholders but ${arguments.size() - firstValue} values: ${sql}".toString())
        }
        sqlAppender.append('(')
        int index = 0
        for (int i = 0; i < placeholders.size(); i++) {
            int placeholder = placeholders.get(i)
            sqlAppender.append(sql, index, placeholder)
            arguments.get(firstValue + i).accept(walker)
            index = placeholder + 1
        }
        sqlAppender.append(sql, index, sql.length())
        if (SqlRestriction.endsInLineComment(sql)) {
            sqlAppender.append('\n')
        }
        sqlAppender.append(')')
    }

    @PackageScope
    static String tableAlias(SqlAstNode column) {
        SqlAstNode node = column instanceof SqlTuple ? ((SqlTuple) column).expressions.get(0) : column
        ColumnReference columnReference = node instanceof Expression ? ((Expression) node).columnReference : null
        if (columnReference != null && columnReference.qualifier != null) {
            return columnReference.qualifier
        }
        throw new IllegalStateException("Cannot resolve the table alias for {alias} from ${column}".toString())
    }

}
