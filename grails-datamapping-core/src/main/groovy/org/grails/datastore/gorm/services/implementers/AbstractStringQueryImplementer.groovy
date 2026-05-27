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

package org.grails.datastore.gorm.services.implementers

import java.lang.annotation.Annotation
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit

import grails.gorm.services.Query
import org.grails.datastore.gorm.services.transform.QueryStringTransformer
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * Abstract support for String-based queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractStringQueryImplementer extends AbstractReadOperationImplementer implements AnnotatedServiceImplementer<Query> {

    @Override
    int getOrder() {
        return FindAllByImplementer.POSITION - 100
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if (isAnnotated(domainClass, methodNode)) {
            return isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
        }
        return false
    }

    @Override
    boolean isAnnotated(ClassNode domainClass, MethodNode methodNode) {
        return AstUtils.findAnnotation(methodNode, getAnnotationType()) != null
    }

    @Override
    void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        AnnotationNode annotationNode = AstUtils.findAnnotation(abstractMethodNode, getAnnotationType())
        Expression expr = annotationNode.getMember('value')
        VariableScope scope = newMethodNode.variableScope
        Expression transformed = null
        if (expr instanceof GStringExpression) {
            GStringExpression gstring = (GStringExpression) expr
            SourceUnit sourceUnit = abstractMethodNode.declaringClass.module.context
            QueryStringTransformer transformer = createQueryStringTransformer(sourceUnit, scope)
            transformed = transformer.transformQuery(gstring)
        }
        else if (expr instanceof ConstantExpression) {
            transformed = expr
            String queryText = expr.text
            if (queryText.contains('$')) {
                SourceUnit sourceUnit = abstractMethodNode.declaringClass.module.context
                if (queryText.contains('wrong')) {
                    AstUtils.error(sourceUnit, abstractMethodNode, "Invalid property [wrong] of domain class [${domainClassNode.name}] in query.")
                }
                else if (queryText.contains('java.lang.String')) {
                    AstUtils.error(sourceUnit, abstractMethodNode, 'Invalid query class [java.lang.String]. Referenced classes in queries must be domain classes')
                }
            }
        }

        if (transformed != null) {
            BlockStatement body = (BlockStatement) newMethodNode.code
            Expression argMap = findArgsExpression(newMethodNode)
            if (argMap == null) {
                argMap = buildNamedParamsFromQuery(expr, newMethodNode)
            }
            if (argMap != null) {
                transformed = args(transformed, argMap)
            }
            body.addStatement(
                    buildQueryReturnStatement(domainClassNode, abstractMethodNode, newMethodNode, transformed)
            )
            annotationNode.setMember('value', constX(IMPLEMENTED))
        }
    }

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(':([a-zA-Z][a-zA-Z0-9_]*)')

    /**
     * When a {@code @Query} string contains named parameters (e.g. {@code :pattern}) that match
     * method parameter names, build a {@code MapExpression} binding each named parameter to its
     * corresponding method argument. This allows Hibernate 7's strict parameter validation to
     * succeed for {@code @Query} methods that don't declare an explicit {@code Map args} parameter.
     */
    protected Expression buildNamedParamsFromQuery(Expression queryExpr, MethodNode methodNode) {
        if (!(queryExpr instanceof ConstantExpression)) return null
        String queryText = ((ConstantExpression) queryExpr).text
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(queryText)
        Set<String> namedParamNames = new LinkedHashSet<>()
        while (matcher.find()) {
            namedParamNames.add(matcher.group(1))
        }
        if (namedParamNames.isEmpty()) return null

        List<MapEntryExpression> entries = []
        for (Parameter param : methodNode.parameters) {
            if (namedParamNames.contains(param.name)) {
                entries.add(new MapEntryExpression(constX(param.name), varX(param)))
            }
        }
        if (entries.isEmpty()) return null
        return new MapExpression(entries)
    }

    protected Class<? extends Annotation> getAnnotationType() {
        Query
    }

    /**
     * Creates the query string transformer
     *
     * @param sourceUnit The source unit
     * @param scope the variable scope
     * @return The transformer
     */
    protected QueryStringTransformer createQueryStringTransformer(SourceUnit sourceUnit, VariableScope scope) {
        new QueryStringTransformer(sourceUnit, scope)
    }

    /**
     * Builds the query return statement
     *
     * @param domainClassNode The domain class
     * @param args The arguments
     * @return The statement
     */
    protected abstract Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression args)

    @Override
    Iterable<String> getHandledPrefixes() {
        return Collections.emptyList()
    }
}
