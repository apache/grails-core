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
package org.grails.datastore.gorm.query.transform

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases

import spock.lang.Specification

/**
 * {@link WhereQueryFunctionCallSpec} covers a SQL function such as {@code year(...)} wrapping a
 * single-level association property (e.g. {@code year(author.birthDate)}), which is routed through the
 * {@code objectExpression instanceof VariableExpression} branch of
 * {@code DetachedCriteriaTransformer#handleAssociationQueryViaPropertyExpression}. Wrapping a function
 * call around a TWO-level association path (e.g. {@code year(author.publisher.foundedDate)}) instead
 * walks the nested {@code while (objectExpression instanceof PropertyExpression)} loop that resolves the
 * root variable and replays each path segment as a {@code delegate.<association> { ... }} call, and only
 * at the innermost segment dispatches to {@code handleFunctionCall} - a distinct branch from the one
 * {@link WhereQueryAssociationPathSpec}'s multi-level test exercises (that test compares the association
 * property directly, with no wrapping function, so it never reaches {@code handleFunctionCall} at all).
 * <p>
 * Like all association criteria, executing the resulting query needs a GORM-enhanced entity that this
 * module deliberately has none of, so the association walk itself is verified structurally (source
 * compiles, one nested closure per path segment - as in {@link WhereQueryAssociationPathSpec}). The
 * function-call dispatch specifically is verified by compiling to the CANONICALIZATION phase - the same
 * phase the transform runs in - and inspecting the resulting AST directly for the
 * {@code FunctionCallingCriterion} construction that only {@code handleFunctionCall} emits.
 */
class WhereQueryMultiLevelAssociationFunctionSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class MultiLevelFuncQueryService {
    protected DetachedCriteria<MultiLevelFuncBook> findByPublisherFoundedYear(int yr) {
        MultiLevelFuncBook.where {
            year(author.publisher.foundedDate) == yr
        }
    }
}

@Entity
class MultiLevelFuncBook {
    String title
    MultiLevelFuncAuthor author
}

@Entity
class MultiLevelFuncAuthor {
    String name
    MultiLevelFuncPublisher publisher
}

@Entity
class MultiLevelFuncPublisher {
    Date foundedDate
}
'''

    private static List<ASTNode> findQueryClosures(GroovyClassLoader gcl) {
        gcl.loadedClasses.findAll {
            it.name.contains('_findByPublisherFoundedYear_')
        }
    }

    void "a function call through a two-level association path compiles and generates one nested closure per path segment"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()

        when:
        gcl.parseClass(SOURCE)

        then:
        noExceptionThrown()

        and: 'one closure for the outer where-block, one for each of the two association segments walked'
        List<Class<?>> queryClosures = findQueryClosures(gcl).sort { it.name.count('$_closure') }
        queryClosures.size() == 3
        queryClosures.last().name.count('$_closure') == 2
    }

    void "a function call through a two-level association path dispatches to handleFunctionCall, building a FunctionCallingCriterion"() {
        given:
        CompilationUnit unit = new CompilationUnit(new GroovyClassLoader())
        def sourceUnit = unit.addSource('Source.groovy', SOURCE)

        when:
        unit.compile(Phases.CANONICALIZATION)
        ModuleNode moduleNode = sourceUnit.getAST()
        ClassNode serviceClass = moduleNode.classes.find { it.name == 'MultiLevelFuncQueryService' }

        List<ConstructorCallExpression> constructorCalls = []
        def visitor = new CodeVisitorSupport() {
            @Override
            void visitConstructorCallExpression(ConstructorCallExpression call) {
                constructorCalls << call
                super.visitConstructorCallExpression(call)
            }
        }
        def method = serviceClass.methods.find { it.name == 'findByPublisherFoundedYear' }
        method.code.visit(visitor)

        then: 'a FunctionCallingCriterion was constructed for the year() function, applied against the innermost association property'
        constructorCalls.any { it.type.name.endsWith('FunctionCallingCriterion') }

        and: 'the function name was passed through untouched'
        constructorCalls.any { call ->
            call.type.name.endsWith('FunctionCallingCriterion') &&
                    ((ConstantExpression) ((ArgumentListExpression) call.arguments).getExpression(0)).value == 'year'
        }
    }
}
