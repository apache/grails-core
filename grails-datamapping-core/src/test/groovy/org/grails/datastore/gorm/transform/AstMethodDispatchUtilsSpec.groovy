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
package org.grails.datastore.gorm.transform

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import spock.lang.Specification

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_ARGUMENTS

/**
 * {@code AstMethodDispatchUtils} builds AST method-call expressions and, when it can resolve the
 * target method on the declared type, wires the {@code MethodTarget} onto the call so the compiler
 * doesn't have to do dynamic dispatch. This spec proves that resolution happens when the method
 * genuinely exists on the target type, is left unresolved when it doesn't, and that the small
 * argument/parameter-building helpers (`namedArgs`, `paramsForArgs`) produce the shapes their callers
 * across the module (service implementers, `TenantTransform`, the transactional transforms) rely on.
 */
class AstMethodDispatchUtilsSpec extends Specification {

    static class Greeter {
        String greet(String name) { "hello $name" }
    }

    void "namedArgs builds a MapExpression with one entry per named argument"() {
        given:
        ConstantExpression trueExpr = ConstantExpression.TRUE

        when:
        MapExpression mapExpression = AstMethodDispatchUtils.namedArgs(failOnError: trueExpr)

        then:
        mapExpression.mapEntryExpressions.size() == 1
        mapExpression.mapEntryExpressions[0].keyExpression.text == 'failOnError'
        mapExpression.mapEntryExpressions[0].valueExpression.is(trueExpr)
    }

    void "callD(Class, var, methodName) resolves the method target when the method exists on the target type"() {
        when:
        MethodCallExpression call = AstMethodDispatchUtils.callD(Greeter, 'greeter', 'greet', args(constX('World')))

        then:
        call.methodAsString == 'greet'
        call.methodTarget != null
        call.methodTarget.name == 'greet'
        call.objectExpression instanceof VariableExpression
        ((VariableExpression) call.objectExpression).name == 'greeter'
    }

    void "callD(ClassNode, var, methodName) leaves the method target unset when the method does not exist"() {
        given:
        ClassNode greeterType = ClassHelper.make(Greeter)

        when:
        MethodCallExpression call = AstMethodDispatchUtils.callD(greeterType, 'greeter', 'doesNotExist')

        then:
        call.methodAsString == 'doesNotExist'
        call.methodTarget == null
    }

    void "callD(Expression, methodName) resolves against the expression's static type"() {
        given:
        VariableExpression target = varX('greeter', ClassHelper.make(Greeter))

        when:
        MethodCallExpression call = AstMethodDispatchUtils.callD(target, 'greet', args(constX('World')))

        then:
        call.methodTarget != null
        call.methodTarget.name == 'greet'
    }

    void "callD defaults to ZERO_ARGUMENTS when no arguments are supplied"() {
        when:
        MethodCallExpression call = AstMethodDispatchUtils.callD(Greeter, 'greeter', 'greet')

        then:
        call.arguments.is(ZERO_ARGUMENTS)
    }

    void "callThisD(Class, methodName) builds a call on an explicit 'this' of the given type and resolves the target"() {
        when:
        MethodCallExpression call = AstMethodDispatchUtils.callThisD(Greeter, 'greet', args(constX('World')))

        then:
        call.methodTarget != null
        call.methodTarget.name == 'greet'
        ((VariableExpression) call.objectExpression).name == 'this'
        ((VariableExpression) call.objectExpression).type == ClassHelper.make(Greeter)
    }

    void "callThisD(ClassNode, methodName, arguments) leaves the method target unset when the method does not exist"() {
        given:
        ClassNode greeterType = ClassHelper.make(Greeter)

        when:
        MethodCallExpression call = AstMethodDispatchUtils.callThisD(greeterType, 'doesNotExist', ZERO_ARGUMENTS)

        then:
        call.methodTarget == null
    }

    void "paramsForArgs builds one parameter per expression in a TupleExpression, typed from each expression"() {
        given:
        TupleExpression tuple = args(constX('a string'), constX(1))

        when:
        Parameter[] params = AstMethodDispatchUtils.paramsForArgs(tuple)

        then:
        params.length == 2
        params[0].name == 'p0'
        params[0].type == ClassHelper.STRING_TYPE
        params[1].name == 'p1'
        params[1].type == ClassHelper.Integer_TYPE
    }

    void "paramsForArgs treats a ClassExpression argument as typed Class, not the referenced type"() {
        given:
        Expression classArg = new ClassExpression(ClassHelper.make(Greeter))
        TupleExpression tuple = args(classArg)

        when:
        Parameter[] params = AstMethodDispatchUtils.paramsForArgs(tuple)

        then:
        params.length == 1
        params[0].type == ClassHelper.CLASS_Type
    }

    void "paramsForArgs builds a single parameter for a bare (non-tuple) expression"() {
        given:
        Expression singleArg = constX('solo')

        when:
        Parameter[] params = AstMethodDispatchUtils.paramsForArgs(singleArg)

        then:
        params.length == 1
        params[0].name == 'p'
        params[0].type == ClassHelper.STRING_TYPE
    }
}
