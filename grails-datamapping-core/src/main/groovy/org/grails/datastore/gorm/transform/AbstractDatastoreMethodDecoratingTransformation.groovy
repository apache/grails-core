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

import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.control.SourceUnit

import org.springframework.beans.factory.annotation.Autowired

import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.reflect.AstUtils

import static org.apache.groovy.ast.tools.AnnotatedNodeUtils.markAsGenerated
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE
import static org.codehaus.groovy.ast.ClassHelper.VOID_TYPE
import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.block
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifElseS
import static org.codehaus.groovy.ast.tools.GeneralUtils.indexX
import static org.codehaus.groovy.ast.tools.GeneralUtils.notNullX
import static org.codehaus.groovy.ast.tools.GeneralUtils.param
import static org.codehaus.groovy.ast.tools.GeneralUtils.params
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.callD
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_PARAMETERS
import static org.grails.datastore.mapping.reflect.AstUtils.isSpockTest

/**
 * An abstract implementation for transformations that decorate a method invocation such that
 * the method invocation is wrapped in the execution of a closure that delegates to the original logic.
 * Examples of such transformations are {@link grails.gorm.transactions.Transactional} and {@link grails.gorm.multitenancy.CurrentTenant}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractDatastoreMethodDecoratingTransformation extends AbstractMethodDecoratingTransformation {

    public static final String FIELD_TARGET_DATASTORE = '$targetDatastore'
    public static final String METHOD_GET_TARGET_DATASTORE = 'getTargetDatastore'
    protected static final String METHOD_GET_DATASTORE_FOR_CONNECTION = 'getDatastoreForConnection'

    @Override
    protected void enhanceClassNode(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode) {
        def appliedMarker = getAppliedMarker()
        if (declaringClassNode.getNodeMetaData(appliedMarker) == appliedMarker) {
            return
        }
        if (declaringClassNode.isInterface()) {
            return
        }
        declaringClassNode.putNodeMetaData(appliedMarker, appliedMarker)

        Expression connectionName = annotationNode.getMember('connection')
        boolean hasDataSourceProperty = connectionName != null
        boolean isSpockTest = isSpockTest(declaringClassNode)
        MethodCallExpression registryExpr = new MethodCallExpression(classX(GormRegistry), 'getInstance', new ArgumentListExpression())
        Expression apiResolverExpr = new MethodCallExpression(registryExpr, 'getApiResolver', new ArgumentListExpression())

        Expression datastoreAttribute = annotationNode.getMember('datastore')
        ClassNode defaultType = hasDataSourceProperty ? make(MultipleConnectionSourceCapableDatastore) : make(Datastore)
        boolean hasSpecificDatastore = datastoreAttribute instanceof ClassExpression
        ClassNode datastoreType = hasSpecificDatastore ? ((ClassExpression) datastoreAttribute).getType().getPlainNodeReference() : defaultType
        Parameter connectionNameParam = param(STRING_TYPE, 'connectionName')
        MethodCallExpression datastoreLookupCall
        MethodCallExpression datastoreLookupDefaultCall
        if (hasSpecificDatastore) {
            datastoreLookupDefaultCall = callD(apiResolverExpr, 'findDatastoreByType', classX(datastoreType.getPlainNodeReference()))
        }
        else {
            datastoreLookupDefaultCall = callD(apiResolverExpr, 'findSingleDatastore')
        }
        datastoreLookupCall = callD(datastoreLookupDefaultCall, METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam))

        Parameter[] getTargetDatastoreParams = params(connectionNameParam)

        FieldNode targetDatastoreField = declaringClassNode.getField(FIELD_TARGET_DATASTORE)
        if (targetDatastoreField == null) {
            targetDatastoreField = declaringClassNode.addField(FIELD_TARGET_DATASTORE, Modifier.PRIVATE, datastoreType, null)
            markAsGenerated(declaringClassNode, targetDatastoreField)
        }

        if (declaringClassNode.getMethod(METHOD_GET_TARGET_DATASTORE, getTargetDatastoreParams) == null && !AstUtils.hasProperty(declaringClassNode, 'targetDatastore')) {
            // When $targetDatastore is explicitly set (e.g. by setTargetDatastore), it is the authoritative
            // parent multi-datasource datastore and must be used for connection routing. Falling back to the
            // API resolver can return a child datastore that doesn't know about sibling connections.
            ClassNode multipleConnectionDatastoreClassNode = make('org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore')
            Expression targetDatastoreExpr = varX(targetDatastoreField)
            Expression lookupDefaultDatastoreExpr = datastoreLookupDefaultCall
            org.codehaus.groovy.ast.stmt.Statement datastoreLookupCallWithConnection = ifElseS(
                instanceofX(lookupDefaultDatastoreExpr, multipleConnectionDatastoreClassNode),
                returnS(callD(castX(multipleConnectionDatastoreClassNode, lookupDefaultDatastoreExpr), METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam))),
                returnS(callD(lookupDefaultDatastoreExpr, METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam)))
            )
            MethodNode mn = declaringClassNode.addMethod(METHOD_GET_TARGET_DATASTORE, Modifier.PUBLIC, datastoreType, getTargetDatastoreParams, null,
                    ifElseS(
                        notNullX(targetDatastoreExpr),
                        ifElseS(
                            instanceofX(targetDatastoreExpr, multipleConnectionDatastoreClassNode),
                            returnS(callD(castX(multipleConnectionDatastoreClassNode, targetDatastoreExpr), METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam))),
                            returnS(callD(targetDatastoreExpr, METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam)))
                        ),
                        datastoreLookupCallWithConnection
                    )
            )
            markAsGenerated(declaringClassNode, mn)
            if (!isSpockTest) {
                compileMethodStatically(source, mn)
            }
        }
        if (declaringClassNode.getMethod(METHOD_GET_TARGET_DATASTORE, ZERO_PARAMETERS) == null && !AstUtils.hasProperty(declaringClassNode, 'targetDatastore')) {
            MethodNode mn = declaringClassNode.addMethod(METHOD_GET_TARGET_DATASTORE, Modifier.PUBLIC,  datastoreType, ZERO_PARAMETERS, null,
                    ifElseS(notNullX(varX(targetDatastoreField)), returnS(varX(targetDatastoreField)), returnS(datastoreLookupDefaultCall))
            )
            markAsGenerated(declaringClassNode, mn)

            if (!isSpockTest) {
                compileMethodStatically(source, mn)
            }
        }

        // Add setter for single datastore
        Parameter datastoreParam = param(datastoreType, 'd')
        if (declaringClassNode.getMethod('setTargetDatastore', params(datastoreParam)) == null) {
            BlockStatement setTargetDatastoreBody = block()
            setTargetDatastoreBody.addStatement(
                    assignS(varX(targetDatastoreField), varX(datastoreParam))
            )
            weaveSetTargetDatastoreBody(source, annotationNode, declaringClassNode, varX(datastoreParam), setTargetDatastoreBody)
            MethodNode setterNode = declaringClassNode.addMethod('setTargetDatastore', Modifier.PUBLIC, VOID_TYPE, params(datastoreParam), null, setTargetDatastoreBody)
            markAsGenerated(declaringClassNode, setterNode)
        }

        // Add dummy setter for compatibility
        Parameter datastoresParam = param(datastoreType.makeArray(), 'datastores')
        if (declaringClassNode.getMethod('setTargetDatastore', params(datastoresParam)) == null) {
            BlockStatement setTargetDatastoresBody = block()
            VariableExpression firstDatastore = varX('first')
            setTargetDatastoresBody.addStatement(
                    declS(firstDatastore, indexX(varX(datastoresParam), constX(0)))
            )
            setTargetDatastoresBody.addStatement(
                    assignS(varX(targetDatastoreField), firstDatastore)
            )
            weaveSetTargetDatastoreBody(source, annotationNode, declaringClassNode, firstDatastore, setTargetDatastoresBody)

            MethodNode setterNode = declaringClassNode.addMethod('setTargetDatastore', Modifier.PUBLIC, VOID_TYPE, params(datastoresParam), null, setTargetDatastoresBody)
            markAsGenerated(declaringClassNode, setterNode)
            if (!AstUtils.hasAnnotation(setterNode, Autowired)) {
                AnnotationNode autowired = new AnnotationNode(make(Autowired))
                autowired.addMember('required', constX(false))
                setterNode.addAnnotation(autowired)
            }
        }
        return
        }

    protected void weaveSetTargetDatastoreBody(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode, Expression datastoreVar, BlockStatement setTargetDatastoreBody) {
        // no-op
    }

    private static Expression instanceofX(Expression objectExpression, ClassNode type) {
        return org.codehaus.groovy.ast.tools.GeneralUtils.binX(
            objectExpression,
            org.codehaus.groovy.ast.tools.GeneralUtils.INSTANCEOF,
            classX(type)
        )
    }

}
