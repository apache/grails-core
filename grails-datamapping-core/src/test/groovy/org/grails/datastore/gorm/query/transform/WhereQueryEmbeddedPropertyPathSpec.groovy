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
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases

import spock.lang.Specification

/**
 * {@link WhereQueryEmbeddedBlockTransformSpec} covers querying an embedded (non-domain, plain Groovy)
 * property via the block-call syntax ({@code extRef { value == search }}), which
 * {@code DetachedCriteriaTransformer} routes through {@code handleAssociationMethodCallExpression}. A
 * direct DOTTED comparison against an embedded property with no block ({@code extRef.value == "x"})
 * instead goes through the {@code AstUtils.isGroovyType(type)} branch of
 * {@code #handleAssociationQueryViaPropertyExpression} - a distinct code path from both the block-call
 * form and the domain-association branch that immediately follows it in the same method (the one
 * {@link WhereQueryAssociationPathSpec} and {@link WhereQueryFunctionCallSpec} exercise for real
 * associations).
 * <p>
 * Like the other association branches, executing the rewritten query needs a GORM-enhanced entity this
 * module has none of, so these are verified by compiling to the CANONICALIZATION phase - the phase the
 * transform itself runs in - and inspecting the resulting AST for the {@code eq('value', 'x')} call the
 * embedded branch generates, or for the compile error the branch's own unknown-property check raises.
 */
class WhereQueryEmbeddedPropertyPathSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class EmbedPathQueryService {
    protected DetachedCriteria<EmbedPathBook> findByRefValue(String search) {
        EmbedPathBook.where {
            extRef.value == search
        }
    }

    protected DetachedCriteria<EmbedPathBook> findByRefPublishedYear(int yr) {
        EmbedPathBook.where {
            year(extRef.published) == yr
        }
    }
}

@Entity
class EmbedPathBook {
    String description
    EmbedPathExternalRef extRef

    static embedded = ['extRef']
}

class EmbedPathExternalRef {
    String provider
    String value
    Date published
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

    private static String constantArg(MethodCallExpression call, int index) {
        Expression arg = ((ArgumentListExpression) call.arguments).getExpression(index)
        ((ConstantExpression) arg).value as String
    }

    void "a direct dotted comparison against an embedded property compiles and generates a nested embedded closure"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()

        when:
        gcl.parseClass(SOURCE)

        then:
        noExceptionThrown()

        and: 'a nested closure was synthesized for the embedded delegate call'
        List<Class<?>> queryClosures = gcl.loadedClasses.findAll {
            it.name.contains('_findByRefValue_')
        }.sort { it.name.count('$_closure') }
        queryClosures.size() == 2
        queryClosures.last().name.count('$_closure') == 1
    }

    void "a direct dotted comparison against an embedded property rewrites to an eq call on the embedded property name"() {
        given:
        ClassNode classNode = compileToClassNode(SOURCE, 'EmbedPathQueryService')

        when:
        def method = classNode.methods.find { it.name == 'findByRefValue' }
        List<MethodCallExpression> eqCalls = methodCallsIn(method.code).findAll { it.methodAsString == 'eq' }

        then: 'the embedded property name (not the full "extRef.value" path) was used as the criterion property'
        eqCalls.any { constantArg(it, 0) == 'value' }
    }

    void "a function call through an embedded property rewrites to a FunctionCallingCriterion on the embedded property name"() {
        given:
        ClassNode classNode = compileToClassNode(SOURCE, 'EmbedPathQueryService')

        when:
        def method = classNode.methods.find { it.name == 'findByRefPublishedYear' }
        List<ConstructorCallExpression> constructorCalls = []
        def visitor = new CodeVisitorSupport() {
            @Override
            void visitConstructorCallExpression(ConstructorCallExpression call) {
                constructorCalls << call
                super.visitConstructorCallExpression(call)
            }
        }
        method.code.visit(visitor)

        then:
        constructorCalls.any { it.type.name.endsWith('FunctionCallingCriterion') }
    }

    void "querying an unknown property on an embedded association fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class EmbedPathUnknownPropBook {
    String description
    EmbedPathUnknownPropExternalRef extRef

    static embedded = ['extRef']

    static findInvalid() {
        EmbedPathUnknownPropBook.where {
            extRef.unknownProperty == "x"
        }
    }
}

class EmbedPathUnknownPropExternalRef {
    String provider
    String value
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot query property "unknownProperty"')
    }
}
