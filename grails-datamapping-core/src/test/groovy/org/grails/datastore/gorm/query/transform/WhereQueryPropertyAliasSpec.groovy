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
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases

import spock.lang.Specification

/**
 * A comparison in a where-block that names a nested path of a local alias, {@code t.club.id} for
 * {@code def t = Team}, is rewritten by {@code DetachedCriteriaTransformer} into an {@code *Property} comparison
 * built from the alias text, as a comparison with {@code t.id} is. A chain that does not start at a variable stays
 * a plain value comparison. The source is compiled to the CANONICALIZATION phase, the phase the transform runs in,
 * and the AST is inspected for the call it generated; the alias methods need a live entity that this module has none of.
 */
class WhereQueryPropertyAliasSpec extends Specification {

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

    private static List<MethodCallExpression> methodCallsNamed(ClassNode classNode, String methodName, String staticMethodName) {
        def method = classNode.methods.find { it.name == staticMethodName }
        methodCallsIn(method.code).findAll { it.methodAsString == methodName }
    }

    private static String constantArg(MethodCallExpression call, int index) {
        Expression arg = ((ArgumentListExpression) call.arguments).getExpression(index)
        ((ConstantExpression) arg).value as String
    }

    void "comparing a plain property against a nested alias path produces an eqProperty call"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasNestedVarBook {
    String someStringProperty
    String otherProperty

    static findNestedAliasVarMatch() {
        AliasNestedVarBook.where {
            def t = AliasNestedVarBook
            otherProperty == t.nested.whatever
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasNestedVarBook')
        List<MethodCallExpression> eqPropertyCalls = methodCallsNamed(classNode, 'eqProperty', 'findNestedAliasVarMatch')

        then: 'the nested alias chain on the right-hand side resolves to root alias t and produces eqProperty'
        eqPropertyCalls.any { constantArg(it, 0) == 'otherProperty' && constantArg(it, 1) == 't.nested.whatever' }
    }

    void "comparing an association property against a nested alias path produces an eqProperty call"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasNestedAssocBook {
    String someStringProperty
    AliasAssocPropAuthor someAssociation

    static findNestedAliasAssocMatch() {
        AliasNestedAssocBook.where {
            def t = AliasNestedAssocBook
            someAssociation.assocProp == t.nested.whatever
        }
    }
}

@Entity
class AliasAssocPropAuthor {
    String assocProp
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasNestedAssocBook')
        List<MethodCallExpression> eqPropertyCalls = methodCallsNamed(classNode, 'eqProperty', 'findNestedAliasAssocMatch')

        then: 'the association property comparison against the nested alias chain produces eqProperty'
        eqPropertyCalls.any { constantArg(it, 0) == 'assocProp' && constantArg(it, 1) == 't.nested.whatever' }
    }

    void "comparing a property against a property chain not rooted at a variable produces an eq call"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasNonVarRootBook {
    String otherProperty

    static Map holder() { [nested: [whatever: 'x']] }

    static findNonVarRootMatch() {
        AliasNonVarRootBook.where {
            def t = AliasNonVarRootBook
            otherProperty == holder().nested.whatever
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasNonVarRootBook')
        List<MethodCallExpression> eqPropertyCalls = methodCallsNamed(classNode, 'eqProperty', 'findNonVarRootMatch')
        List<MethodCallExpression> eqCalls = methodCallsNamed(classNode, 'eq', 'findNonVarRootMatch')

        then: 'the right-hand chain does not start at a variable and falls back to eq'
        eqPropertyCalls.isEmpty()
        eqCalls.any { constantArg(it, 0) == 'otherProperty' }
    }
}
