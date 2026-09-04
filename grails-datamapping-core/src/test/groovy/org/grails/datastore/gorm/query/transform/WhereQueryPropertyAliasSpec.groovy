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
 * A where-block can declare a local alias for a property (e.g. {@code def t = someProperty}, handled by
 * {@code DetachedCriteriaTransformer#addStatementToNewQuery} around its {@code createAlias}-generating
 * declaration branch - see {@link WhereQueryStaticFieldSpec}) or a self-alias for the whole domain class
 * (e.g. {@code def a = SomeDomain}, which calls {@code this.setAlias(...)} and records the alias against
 * the class itself rather than a property name). A later comparison that references either kind of alias
 * on the right-hand side is rewritten by {@code DetachedCriteriaTransformer#addCriteriaCall} /
 * {@code #handleAssociationQueryViaPropertyExpression} into an {@code *Property} comparison method
 * (eqProperty, gtProperty, ...) built from the alias text directly, rather than a plain value comparison.
 * <p>
 * Executing the resulting query would require a live, GORM-enhanced {@code PersistentEntity} - the alias
 * methods ({@code createAlias}, {@code setAlias}) that back these branches resolve association metadata at
 * runtime, which this module deliberately has none of (see the note on
 * {@code WhereQueryStaticFieldSpec#"assigning an existing property name..."}). These branches are
 * therefore verified precisely but without execution: the source is compiled to the CANONICALIZATION
 * phase - the same phase the transform itself runs in - and the resulting AST is inspected directly for
 * the generated {@code *Property} method call, which proves the branch under test produced exactly the
 * rewrite expected rather than merely that the source happened to compile.
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

    void "comparing an association property against a property-name alias produces an eqProperty call"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasAssocPropBook {
    String someStringProperty
    AliasAssocPropAuthor someAssociation

    static findAliasedAssocMatch() {
        AliasAssocPropBook.where {
            def t = someStringProperty
            someAssociation.assocProp == t.whatever
        }
    }
}

@Entity
class AliasAssocPropAuthor {
    String assocProp
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasAssocPropBook')
        List<MethodCallExpression> eqPropertyCalls = methodCallsNamed(classNode, 'eqProperty', 'findAliasedAssocMatch')

        then: 'the association property comparison against the alias was rewritten to compare against the alias text directly'
        eqPropertyCalls.any { constantArg(it, 0) == 'assocProp' && constantArg(it, 1) == 't.whatever' }
    }

    void "comparing a plain property against a property-name alias produces an eqProperty call"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasVarPropBook {
    String someStringProperty
    String otherProperty

    static findAliasedVarMatch() {
        AliasVarPropBook.where {
            def t = someStringProperty
            otherProperty == t.whatever
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasVarPropBook')
        List<MethodCallExpression> eqPropertyCalls = methodCallsNamed(classNode, 'eqProperty', 'findAliasedVarMatch')

        then: 'the plain property comparison against the alias was rewritten to compare against the alias text directly'
        eqPropertyCalls.any { constantArg(it, 0) == 'otherProperty' && constantArg(it, 1) == 't.whatever' }
    }

    void "comparing two self-aliases of the same domain class against each other produces an eqProperty call"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasSelfBook {
    String someProperty

    static findSelfAliasMatch() {
        AliasSelfBook.where {
            def a = AliasSelfBook
            def b = AliasSelfBook
            a.someProperty == b.someProperty
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasSelfBook')
        List<MethodCallExpression> eqPropertyCalls = methodCallsNamed(classNode, 'eqProperty', 'findSelfAliasMatch')

        then: 'the comparison between the two self-aliased references was rewritten into an eqProperty call'
        eqPropertyCalls.any { constantArg(it, 0) == 'a.someProperty' && constantArg(it, 1) == 'b.someProperty' }
    }
}
