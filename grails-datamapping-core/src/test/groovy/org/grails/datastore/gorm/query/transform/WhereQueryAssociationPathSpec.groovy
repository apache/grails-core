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
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases

import spock.lang.Specification

/**
 * A property-path expression such as {@code author.name} or a deeper {@code author.publisher.name} is
 * rewritten by {@code DetachedCriteriaTransformer#handleAssociationQueryViaPropertyExpression} into
 * nested {@code delegate.<association> { ... }} calls, one per path segment. Those delegate calls are
 * dynamic (routed through {@code AbstractDetachedCriteria#methodMissing}) and need the target class to be
 * GORM-enhanced against a live datastore to resolve - something this module deliberately has none of - so
 * these associations are verified structurally: the source compiles (or fails to, for the invalid-property
 * cases) and by compiling to the CANONICALIZATION phase - the phase the transform itself runs in - and
 * inspecting the resulting AST for the actual nested {@code delegate.<association> { ... }} calls generated,
 * same as {@link WhereQueryEmbeddedPropertyPathSpec}.
 */
class WhereQueryAssociationPathSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SINGLE_LEVEL_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class AssocPathSingleQueryService {
    protected DetachedCriteria<AssocPathSingleBook> findByAuthorName(String name) {
        AssocPathSingleBook.where {
            author.name == name
        }
    }
}

@Entity
class AssocPathSingleBook {
    String title
    AssocPathSingleAuthor author
}

@Entity
class AssocPathSingleAuthor {
    String name
}
'''

    private static final String MULTI_LEVEL_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class AssocPathMultiQueryService {
    protected DetachedCriteria<AssocPathMultiBook> findByPublisherName(String name) {
        AssocPathMultiBook.where {
            author.publisher.name == name
        }
    }
}

@Entity
class AssocPathMultiBook {
    String title
    AssocPathMultiAuthor author
}

@Entity
class AssocPathMultiAuthor {
    String name
    AssocPathMultiPublisher publisher
}

@Entity
class AssocPathMultiPublisher {
    String name
}
'''

    private static ClassNode compileToClassNode(String source, String className) {
        CompilationUnit unit = new CompilationUnit(new GroovyClassLoader())
        def sourceUnit = unit.addSource('Source.groovy', source)
        unit.compile(Phases.CANONICALIZATION)
        ModuleNode moduleNode = sourceUnit.getAST()
        (ClassNode) moduleNode.classes.find { ClassNode cn -> cn.name == className }
    }

    private static List<MethodCallExpression> methodCallsIn(ASTNode node) {
        List<MethodCallExpression> calls = []
        CodeVisitorSupport visitor = new CodeVisitorSupport() {
            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                calls << call
                super.visitMethodCallExpression(call)
            }
        }
        node.visit(visitor)
        calls
    }

    private static List<MethodCallExpression> delegateCallsIn(ASTNode node) {
        methodCallsIn(node).findAll {
            it.objectExpression instanceof VariableExpression && ((VariableExpression) it.objectExpression).name == 'delegate'
        }
    }

    private static ClosureExpression closureArgOf(MethodCallExpression call) {
        (ClosureExpression) ((ArgumentListExpression) call.arguments).expressions.find { it instanceof ClosureExpression }
    }

    private static String constantArg(MethodCallExpression call, int index) {
        Expression arg = ((ArgumentListExpression) call.arguments).getExpression(index)
        ((ConstantExpression) arg).value as String
    }

    void "a single-level association property path rewrites to a delegate.author { } call with an eq on the association property name"() {
        given:
        ClassNode classNode = compileToClassNode(SINGLE_LEVEL_SOURCE, 'AssocPathSingleQueryService')

        when:
        def method = classNode.methods.find { it.name == 'findByAuthorName' }
        MethodCallExpression authorCall = delegateCallsIn(method.code).find { it.methodAsString == 'author' }

        then: 'the single association segment was walked via a delegate.author { ... } call'
        authorCall != null

        and: 'the comparison inside it used the association property name, not the full "author.name" path'
        List<MethodCallExpression> eqCalls = methodCallsIn(closureArgOf(authorCall).code).findAll { it.methodAsString == 'eq' }
        eqCalls.any { constantArg(it, 0) == 'name' }
    }

    void "a multi-level association property path rewrites to nested delegate calls, one per path segment"() {
        given:
        ClassNode classNode = compileToClassNode(MULTI_LEVEL_SOURCE, 'AssocPathMultiQueryService')

        when:
        def method = classNode.methods.find { it.name == 'findByPublisherName' }
        MethodCallExpression authorCall = delegateCallsIn(method.code).find { it.methodAsString == 'author' }

        then: 'the first association segment was walked via a delegate.author { ... } call'
        authorCall != null

        when:
        MethodCallExpression publisherCall = delegateCallsIn(closureArgOf(authorCall).code).find { it.methodAsString == 'publisher' }

        then: 'the second association segment was walked via a delegate.publisher { ... } call nested inside the first'
        publisherCall != null

        and: 'the comparison at the innermost segment used the association property name only'
        List<MethodCallExpression> eqCalls = methodCallsIn(closureArgOf(publisherCall).code).findAll { it.methodAsString == 'eq' }
        eqCalls.any { constantArg(it, 0) == 'name' }
    }

    void "querying an unknown property on a single-level association fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AssocPathUnknownPropBook {
    String title
    AssocPathUnknownPropAuthor author

    static findInvalid() {
        AssocPathUnknownPropBook.where {
            author.unknownProperty == "x"
        }
    }
}

@Entity
class AssocPathUnknownPropAuthor {
    String name
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot query property "unknownProperty"')
    }

    void "querying an unknown top-level property fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AssocPathUnknownTopLevelBook {
    String title

    static findInvalid() {
        AssocPathUnknownTopLevelBook.where {
            unknownProperty == "x"
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot query on property "unknownProperty"')
    }
}
