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

import java.util.function.Supplier

import groovy.transform.CompileStatic
import org.hibernate.metamodel.mapping.BasicValuedMapping
import org.hibernate.metamodel.model.domain.ReturnableType
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor
import org.hibernate.query.sqm.produce.function.FunctionArgumentTypeResolver
import org.hibernate.query.sqm.produce.function.FunctionReturnTypeResolver
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter
import org.hibernate.query.sqm.tree.SqmTypedNode
import org.hibernate.sql.ast.SqlAstTranslator
import org.hibernate.sql.ast.spi.SqlAppender
import org.hibernate.sql.ast.tree.SqlAstNode
import org.hibernate.sql.ast.tree.expression.Literal
import org.hibernate.type.JavaObjectType
import org.hibernate.type.spi.TypeConfiguration

/**
 * Renders the SQL of a {@link SqlProjection} or a {@link SqlGroupProjection} as is. The arguments are the SQL as a
 * literal and, if the SQL contains {@code {alias}}, a column of the queried entity whose table alias replaces it.
 * The function takes the type the criteria query gives it, as Hibernate's own {@code sql} function does.
 *
 * @since 8.0.1
 */
@CompileStatic
class GrailsSqlProjectionFunction extends AbstractSqmSelfRenderingFunctionDescriptor {

    public static final String NAME = 'grails_sql_projection'

    GrailsSqlProjectionFunction() {
        super(NAME, StandardArgumentsValidators.between(1, 2), new ImpliedTypeResolver(), (FunctionArgumentTypeResolver) null)
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
        if (sql.contains(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER)) {
            if (arguments.size() < 2) {
                throw new IllegalArgumentException(
                        "The SQL projection uses {alias} but no column of the queried entity was given: ${sql}".toString())
            }
            sql = sql.replace(GrailsSqlRestrictionFunction.ALIAS_PLACEHOLDER,
                    GrailsSqlRestrictionFunction.tableAlias(arguments.get(1)))
        }
        sqlAppender.appendSql(sql)
        if (SqlRestriction.endsInLineComment(sql)) {
            sqlAppender.appendSql('\n' as char)
        }
    }

    @Override
    String getArgumentListSignature() {
        return ''
    }

    @CompileStatic
    private static final class ImpliedTypeResolver implements FunctionReturnTypeResolver {

        @Override
        ReturnableType<?> resolveFunctionReturnType(
                ReturnableType<?> impliedType,
                SqmToSqlAstConverter converter,
                List<? extends SqmTypedNode<?>> arguments,
                TypeConfiguration typeConfiguration) {
            return impliedType != null ? impliedType : typeConfiguration.getBasicTypeForJavaType(Object)
        }

        @Override
        BasicValuedMapping resolveFunctionReturnType(
                Supplier<BasicValuedMapping> impliedTypeAccess,
                List<? extends SqlAstNode> arguments) {
            BasicValuedMapping impliedType = impliedTypeAccess.get()
            return impliedType != null ? impliedType : JavaObjectType.INSTANCE
        }

    }

}
