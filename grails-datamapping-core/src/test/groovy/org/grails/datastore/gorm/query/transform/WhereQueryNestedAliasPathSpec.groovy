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
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases

import spock.lang.Specification

/**
 * A comparison in a where-block that names a nested path of a local alias, {@code t.club.id} for
 * {@code def t = Team}, is rewritten by {@code DetachedCriteriaTransformer} into an {@code *Property} comparison
 * built from the alias text, as a comparison with {@code t.id} is. A chain that does not start at a variable stays
 * a plain value comparison. The source is compiled to the CANONICALIZATION phase, the phase the transform runs in,
 * and the AST is inspected for the call it generated; the alias methods need a live entity that this module has none of.
 */
class WhereQueryNestedAliasPathSpec extends Specification {

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

    void "comparing an alias of the query's own root against a nested alias path produces an eqProperty call, in either order"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasOwnRootBook {
    String otherProperty

    static findOwnRootFirst() {
        AliasOwnRootBook.where {
            def t = AliasOwnRootBook
            def c = AliasOwnRootBook
            c.id == t.nested.whatever
        }
    }

    static findOwnRootSecond() {
        AliasOwnRootBook.where {
            def t = AliasOwnRootBook
            def c = AliasOwnRootBook
            t.nested.whatever == c.id
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasOwnRootBook')

        then: 'both write the comparison of c.id with the nested path of t'
        methodCallsNamed(classNode, 'eqProperty', 'findOwnRootFirst').any { constantArg(it, 0) == 'c.id' && constantArg(it, 1) == 't.nested.whatever' }
        methodCallsNamed(classNode, 'eqProperty', 'findOwnRootSecond').any { constantArg(it, 0) == 'c.id' && constantArg(it, 1) == 't.nested.whatever' }
    }

    void "an alias declared in one method is not an alias in the next method of the class"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasLeakBook {
    String otherProperty

    static declaresTheAlias() {
        AliasLeakBook.where {
            def t = AliasLeakBook
            otherProperty == t.nested.whatever
        }
    }

    static usesTAsAMap() {
        Map t = [nested: [whatever: 'x']]
        AliasLeakBook.where {
            otherProperty == t.nested.whatever
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasLeakBook')

        then: 'the first method compares properties, the second compares with the value of its local variable'
        methodCallsNamed(classNode, 'eqProperty', 'declaresTheAlias').any { constantArg(it, 1) == 't.nested.whatever' }
        methodCallsNamed(classNode, 'eqProperty', 'usesTAsAMap').isEmpty()
        methodCallsNamed(classNode, 'eq', 'usesTAsAMap').any { constantArg(it, 0) == 'otherProperty' }
    }

    void "an alias declared in one where query is not an alias in a later where query of the same method"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasSameMethodBook {
    String otherProperty

    static twoQueries() {
        AliasSameMethodBook.where {
            def t = AliasSameMethodBook
            otherProperty == t.nested.whatever
        }.list()
        Map t = [nested: [whatever: 'x']]
        AliasSameMethodBook.where {
            otherProperty == t.nested.whatever
        }.list()
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasSameMethodBook')

        then: 'the first query compares properties, the second compares with the value of its local variable'
        methodCallsNamed(classNode, 'setAlias', 'twoQueries').size() == 1
        methodCallsNamed(classNode, 'eqProperty', 'twoQueries').size() == 1
        methodCallsNamed(classNode, 'eq', 'twoQueries').any { constantArg(it, 0) == 'otherProperty' }
    }

    void "an alias declared in one subquery is not an alias in the next subquery of the same query"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasSiblingBook {
    String otherProperty

    static twoSubqueries() {
        AliasSiblingBook.where {
            exists(AliasSiblingBook.where {
                def c = AliasSiblingBook
                otherProperty == c.nested.whatever
            }.property('otherProperty'))
            exists(AliasSiblingBook.where {
                Map c = [nested: [whatever: 'x']]
                otherProperty == c.nested.whatever
            }.property('otherProperty'))
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasSiblingBook')

        then: 'the first subquery compares properties, the second compares with the value of its local variable'
        methodCallsNamed(classNode, 'setAlias', 'twoSubqueries').size() == 1
        methodCallsNamed(classNode, 'eqProperty', 'twoSubqueries').size() == 1
        methodCallsNamed(classNode, 'eq', 'twoSubqueries').any { constantArg(it, 0) == 'otherProperty' }
    }

    void "an alias of a property keeps writing its comparison on the association"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasOfPropertyBook {
    String name
    AliasOfPropertyClub club

    static findByClubName() {
        AliasOfPropertyBook.where {
            def c = club
            c.name == name
        }
    }
}

@Entity
class AliasOfPropertyClub {
    String name
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasOfPropertyBook')

        then: 'the comparison is written inside the block of the association, as before, and not swapped to an alias path'
        methodCallsNamed(classNode, 'createAlias', 'findByClubName').size() == 1
        methodCallsNamed(classNode, 'eqProperty', 'findByClubName').any { constantArg(it, 0) == 'name' && constantArg(it, 1) == 'name' }
        methodCallsNamed(classNode, 'eqProperty', 'findByClubName').every { constantArg(it, 1) != 'c.name' }
    }

    void "comparing a property of an alias with the identifier of the queried class keeps the comparison, in either order"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasPropertyIdBook {
    String otherProperty

    static aliasFirst() {
        AliasPropertyIdBook.where {
            def t = AliasPropertyIdBook
            t.club == id
        }
    }

    static idFirst() {
        AliasPropertyIdBook.where {
            def t = AliasPropertyIdBook
            id == t.club
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasPropertyIdBook')

        then: 'both write the comparison of id with t.club'
        methodCallsNamed(classNode, 'eqProperty', 'aliasFirst').any { constantArg(it, 0) == 'id' && constantArg(it, 1) == 't.club' }
        methodCallsNamed(classNode, 'eqProperty', 'idFirst').any { constantArg(it, 0) == 'id' && constantArg(it, 1) == 't.club' }
    }

    void "an alias declared in a static where field is not an alias in a method of the class"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasFieldLeakBook {
    String otherProperty

    static q = AliasFieldLeakBook.where {
        def t = AliasFieldLeakBook
        otherProperty == t.nested.whatever
    }

    static usesTAsAMap() {
        Map t = [nested: [whatever: 'x']]
        AliasFieldLeakBook.where {
            otherProperty == t.nested.whatever
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasFieldLeakBook')

        then: 'the field still compares the properties, the method compares with the value of its local variable'
        methodCallsIn(classNode.getField('q').initialExpression).any { it.methodAsString == 'eqProperty' && constantArg(it, 1) == 't.nested.whatever' }
        methodCallsNamed(classNode, 'eqProperty', 'usesTAsAMap').isEmpty()
        methodCallsNamed(classNode, 'eq', 'usesTAsAMap').any { constantArg(it, 0) == 'otherProperty' }
    }

    void "a nested alias path compared with a captured local variable is refused, and the property of the same name is compared"() {
        given:
        String captured = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasCapturedLocalBook {
    String title

    static findCaptured() {
        def title = 'x'
        AliasCapturedLocalBook.where {
            def t = AliasCapturedLocalBook
            t.nested.whatever == title
        }
    }
}
'''
        String property = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasPropertyBook {
    String title

    static findProperty() {
        AliasPropertyBook.where {
            def t = AliasPropertyBook
            t.nested.whatever == title
        }
    }
}
'''

        when:
        compileToClassNode(captured, 'AliasCapturedLocalBook')

        then: 'the comparison with a local variable is refused at compile time instead of being dropped from the query'
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot write the comparison')
        e.message.contains('t.nested.whatever == title')

        when:
        ClassNode classNode = compileToClassNode(property, 'AliasPropertyBook')

        then: 'the property of the queried class is compared with the alias path'
        methodCallsNamed(classNode, 'eqProperty', 'findProperty').any { constantArg(it, 0) == 'title' && constantArg(it, 1) == 't.nested.whatever' }
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

    void "an alias of a class compared with an alias path is compared by its identifier, in either order"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasRowBook {
    String title

    static findAliasRight() {
        AliasRowBook.where {
            def t = AliasRowBook
            def c = AliasRowBook
            t.nested == c
        }
    }

    static findAliasLeft() {
        AliasRowBook.where {
            def t = AliasRowBook
            def c = AliasRowBook
            c != t.nested
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasRowBook')

        then: 'the alias stands for its identifier, c.id, as in t.nested == c.id'
        methodCallsNamed(classNode, 'eqProperty', 'findAliasRight').any { [constantArg(it, 0), constantArg(it, 1)] as Set == ['c.id', 't.nested'] as Set }
        methodCallsNamed(classNode, 'neProperty', 'findAliasLeft').any { [constantArg(it, 0), constantArg(it, 1)] as Set == ['c.id', 't.nested'] as Set }
    }

    void "an alias of a class compared with an alias path by an order is still refused"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasRowOrderBook {
    String title

    static findOrdered() {
        AliasRowOrderBook.where {
            def t = AliasRowOrderBook
            def c = AliasRowOrderBook
            t.nested < c
        }
    }
}
'''

        when:
        compileToClassNode(source, 'AliasRowOrderBook')

        then: 'a row has no order, so the comparison is refused instead of being dropped'
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot write the comparison')
    }

    void "a comparison of two alias paths is written once, not again as the right side compared with itself"() {
        given:
        String source = '''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AliasPairBook {
    String title

    static findNotEqualAliasFirst() {
        AliasPairBook.where {
            def t = AliasPairBook
            def c = AliasPairBook
            t.nested != c.id
        }
    }

    static findNotEqualOwnFirst() {
        AliasPairBook.where {
            def t = AliasPairBook
            def c = AliasPairBook
            c.id != t.nested
        }
    }

    static findEqual() {
        AliasPairBook.where {
            def t = AliasPairBook
            def c = AliasPairBook
            t.nested == c.id
        }
    }
}
'''

        when:
        ClassNode classNode = compileToClassNode(source, 'AliasPairBook')

        then: 'one criterion each: a second one compared the right side with itself, c.id <> c.id, and kept no row'
        methodCallsNamed(classNode, 'neProperty', 'findNotEqualAliasFirst').collect { [constantArg(it, 0), constantArg(it, 1)] } == [['t.nested', 'c.id']]
        methodCallsNamed(classNode, 'neProperty', 'findNotEqualOwnFirst').collect { [constantArg(it, 0), constantArg(it, 1)] } == [['c.id', 't.nested']]
        methodCallsNamed(classNode, 'eqProperty', 'findEqual').collect { [constantArg(it, 0), constantArg(it, 1)] } == [['t.nested', 'c.id']]
    }
}
