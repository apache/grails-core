/*
 * Copyright 2013 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.async.transform.internal

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.compiler.web.async.TransactionalAsyncTransformUtils
import grails.transaction.TransactionManagerAware
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional

import java.lang.reflect.Modifier

/**
 * Modifies the @DelegateAsync transform to take into account transactional services. New instance should be created per class transform, as state is held.
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultDelegateAsyncTransactionalMethodTransformer implements DelegateAsyncTransactionalMethodTransformer {

    private static final String TRANSACTIONAL_FIELD = "transactional"
    private static final ClassNode TRANSACTIONAL_CLASS_NODE = new ClassNode(Transactional)
    private static final ClassNode INTERFACE_TRANSACTION_MANAGER = new ClassNode(PlatformTransactionManager).getPlainNodeReference()
    private static final ClassNode INTERFACE_TRANSACTION_MANAGER_AWARE = new ClassNode(TransactionManagerAware).getPlainNodeReference()
    private static final Parameter[] SET_TRANSACTION_MANAGER_METHOD_PARAMETERS = [new Parameter(INTERFACE_TRANSACTION_MANAGER, "transactionManager")] as Parameter[]
    private static final String FIELD_NAME_TRANSACTION_MANAGER = '$transactionManager'
    private static final String FIELD_NAME_PROMISE_DECORATORS = '$promiseDecorators'
    private static final ClassNode CLASS_NODE_MAP = new ClassNode(Map.class).getPlainNodeReference()
    private static final String METHOD_NAME_SET_TRANSACTION_MANAGER = "setTransactionManager"
    private static final VariableExpression EXPRESSION_THIS = new VariableExpression("this")
    private static final Token OPERATOR_ASSIGNMENT = new Token(Types.EQUAL,"=", -1,-1)
    private static final String VARIABLE_TRANSACTION_MANAGER = "txMgr"
    private FieldNode transactionalField
    private boolean isTransactional = false

    private int promiseDecoratorCount = 0

    @Override
    void transformTransactionalMethod(ClassNode classNode,ClassNode delegateClassNode,  MethodNode methodNode, ListExpression promiseDecorators) {

        if (transactionalField == null) {
            transactionalField = delegateClassNode.getField(TRANSACTIONAL_FIELD)
            isTransactional = false
            if(transactionalField) {
                def ie = transactionalField.getInitialExpression()
                if(ie instanceof ConstantExpression) {
                    isTransactional = ie.isTrueExpression()
                }
            }
        }

        final transactionalAnnotations = methodNode.getAnnotations(TRANSACTIONAL_CLASS_NODE)
        if (isTransactional || !transactionalAnnotations.isEmpty()) {
            BlockStatement setTransactionManagerMethodBody = getSetTransactionManagerMethodBody(classNode)

            int currentIndex = promiseDecoratorCount++


            final methodLookupArguments = new ArgumentListExpression(new ConstantExpression(methodNode.getName()))
            for(Parameter p in methodNode.getParameters()) {
                methodLookupArguments.addExpression(new ClassExpression(p.getType().getPlainNodeReference()))
            }
            final promiseLookupExpression = new BinaryExpression(new PropertyExpression(EXPRESSION_THIS, FIELD_NAME_PROMISE_DECORATORS), Token.newSymbol(Types.LEFT_SQUARE_BRACKET, -1, -1), new ConstantExpression(currentIndex))
            setTransactionManagerMethodBody.addStatement(
                new ExpressionStatement(
                    new BinaryExpression(
                        promiseLookupExpression,
                        OPERATOR_ASSIGNMENT,
                        new MethodCallExpression(
                             new ClassExpression(new ClassNode(TransactionalAsyncTransformUtils).getPlainNodeReference()),
                            "createTransactionalPromiseDecorator",
                             new ArgumentListExpression(new VariableExpression(VARIABLE_TRANSACTION_MANAGER),
                                                        new MethodCallExpression(
                                                            new ClassExpression(delegateClassNode),
                                                            "getDeclaredMethod", methodLookupArguments
                                                        )
                             )
                        )

                    )
                )
            )

            promiseDecorators.addExpression(promiseLookupExpression)

        }

    }

    static BlockStatement getSetTransactionManagerMethodBody(ClassNode classNode) {
        def method = classNode.getMethod(METHOD_NAME_SET_TRANSACTION_MANAGER, SET_TRANSACTION_MANAGER_METHOD_PARAMETERS)

        if (method == null) {
            final allInterfaces = classNode.getAllInterfaces()
            if (!allInterfaces.contains(INTERFACE_TRANSACTION_MANAGER_AWARE)) {
                classNode.addInterface(INTERFACE_TRANSACTION_MANAGER_AWARE)
            }
            final transactionManagerField = classNode.getField(FIELD_NAME_TRANSACTION_MANAGER)
            if (transactionManagerField == null) {
                classNode.addField(new FieldNode(FIELD_NAME_TRANSACTION_MANAGER, Modifier.PRIVATE, INTERFACE_TRANSACTION_MANAGER, classNode, GrailsASTUtils.NULL_EXPRESSION))
            }
            final promiseDecoratorsField = classNode.getField(FIELD_NAME_PROMISE_DECORATORS)
            if(promiseDecoratorsField == null) {
                classNode.addField(new FieldNode(FIELD_NAME_PROMISE_DECORATORS, Modifier.PRIVATE, CLASS_NODE_MAP, classNode, new MapExpression()))
            }

            final methodBody = new BlockStatement()
            final transactionManagerParameter = new Parameter(INTERFACE_TRANSACTION_MANAGER, "transactionManager")
            def parameters = [transactionManagerParameter] as Parameter[]
            final txMgrParam = new VariableExpression(transactionManagerParameter)
            methodBody.addStatement(
                new ExpressionStatement(
                    new BinaryExpression(
                        new PropertyExpression(EXPRESSION_THIS, FIELD_NAME_TRANSACTION_MANAGER),
                        OPERATOR_ASSIGNMENT,
                        txMgrParam
                    )
                )
            )
            methodBody.addStatement(
                new ExpressionStatement(
                    new DeclarationExpression(
                        new VariableExpression(VARIABLE_TRANSACTION_MANAGER, INTERFACE_TRANSACTION_MANAGER),
                        OPERATOR_ASSIGNMENT,
                        txMgrParam
                    )
                )
            )
            method = new MethodNode(METHOD_NAME_SET_TRANSACTION_MANAGER, Modifier.PUBLIC, ClassHelper.VOID_TYPE, parameters, [] as ClassNode[], methodBody)
            classNode.addMethod(method)

        }

        return (BlockStatement)method.getCode()
    }
}
