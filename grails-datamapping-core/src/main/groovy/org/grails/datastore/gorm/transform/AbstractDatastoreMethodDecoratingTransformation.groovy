/*
 * Copyright 2017 the original author or authors.
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
package org.grails.datastore.gorm.transform

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.internal.RuntimeSupport
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.reflect.AstUtils
import org.springframework.beans.factory.annotation.Autowired

import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.*
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.callD
import static org.grails.datastore.mapping.reflect.AstUtils.*

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
    public static final String METHOD_GET_TARGET_DATASTORE = "getTargetDatastore"
    protected static final String METHOD_GET_DATASTORE_FOR_CONNECTION = "getDatastoreForConnection"


    @Override
    protected void enhanceClassNode(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode) {
        def appliedMarker = getAppliedMarker()
        if(declaringClassNode.getNodeMetaData(appliedMarker) == appliedMarker) {
            return
        }
        if(declaringClassNode.isInterface()) {
            return
        }
        declaringClassNode.putNodeMetaData(appliedMarker, appliedMarker)

        Expression connectionName = annotationNode.getMember("connection")
        boolean hasDataSourceProperty = connectionName != null
        boolean isSpockTest = isSpockTest(declaringClassNode)
        ClassExpression gormEnhancerExpr = classX(GormEnhancer)

        Expression datastoreAttribute = annotationNode.getMember("datastore")
        ClassNode defaultType = hasDataSourceProperty ? make(MultipleConnectionSourceCapableDatastore) : make(Datastore)
        boolean hasSpecificDatastore = datastoreAttribute instanceof ClassExpression
        ClassNode datastoreType = hasSpecificDatastore ? ((ClassExpression)datastoreAttribute).getType().getPlainNodeReference() : defaultType
        Parameter connectionNameParam = param(STRING_TYPE, "connectionName")
        MethodCallExpression datastoreLookupCall
        MethodCallExpression datastoreLookupDefaultCall
        if(hasSpecificDatastore) {
            datastoreLookupDefaultCall = callD(gormEnhancerExpr, "findDatastoreByType", classX(datastoreType.getPlainNodeReference()))
        }
        else {
            datastoreLookupDefaultCall = callD(gormEnhancerExpr, "findSingleDatastore")
        }
        datastoreLookupCall = callD(datastoreLookupDefaultCall, METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam))

        if(implementsInterface(declaringClassNode, "org.grails.datastore.mapping.services.Service")) {
            // simplify logic for services
            Parameter[] getTargetDatastoreParams = params(connectionNameParam)
            VariableExpression datastoreVar = varX("datastore", make(Datastore))

            // Add method:
            // protected Datastore getTargetDatastore(String connectionName)
            //    if(datastore != null)
            //      return datastore.getDatastoreForConnection(connectionName)
            //    else
            //      return GormEnhancer.findSingleDatastore().getDatastoreForConnection(connectionName)

            if(declaringClassNode.getMethod(METHOD_GET_TARGET_DATASTORE, getTargetDatastoreParams) == null) {
                MethodNode mn = declaringClassNode.addMethod(METHOD_GET_TARGET_DATASTORE, Modifier.PROTECTED, datastoreType, getTargetDatastoreParams, null,
                        ifElseS(notNullX(datastoreVar),
                                returnS( callD( castX(make(MultipleConnectionSourceCapableDatastore), datastoreVar ), METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam) ) ),
                                returnS(datastoreLookupCall)
                        ))
                compileMethodStatically(source, mn)
            }
            if(declaringClassNode.getMethod(METHOD_GET_TARGET_DATASTORE, ZERO_PARAMETERS) == null) {
                MethodNode mn = declaringClassNode.addMethod(METHOD_GET_TARGET_DATASTORE, Modifier.PROTECTED,  datastoreType, ZERO_PARAMETERS, null,
                        ifElseS( notNullX(datastoreVar ),
                                returnS(datastoreVar),
                                returnS(datastoreLookupDefaultCall))
                )

                compileMethodStatically(source, mn)
            }
        }
        else {
            FieldNode datastoreField = declaringClassNode.getField(FIELD_TARGET_DATASTORE)
            if(datastoreField == null) {
                datastoreField = declaringClassNode.addField(FIELD_TARGET_DATASTORE, Modifier.PROTECTED, datastoreType, null)

                Parameter datastoresParam = param(datastoreType.makeArray(), "datastores")
                VariableExpression datastoresVar = varX(datastoresParam)
                Expression datastoreVar = callD(classX(RuntimeSupport), "findDefaultDatastore", datastoresVar)

                BlockStatement setTargetDatastoreBody
                VariableExpression datastoreFieldVar = varX(datastoreField)

                Statement assignTargetDatastore = assignS(datastoreFieldVar,datastoreVar )
                if(hasDataSourceProperty) {
                    // $targetDatastore = RuntimeSupport.findDefaultDatastore(datastores)
                    // datastore = datastore.getDatastoreForConnection(connectionName)
                    setTargetDatastoreBody = block(
                            assignTargetDatastore,
                            assignS(datastoreFieldVar, callX(datastoreFieldVar, METHOD_GET_DATASTORE_FOR_CONNECTION, connectionName ))
                    )
                }
                else {
                    setTargetDatastoreBody = block(
                            assignTargetDatastore
                    )
                }

                weaveSetTargetDatastoreBody(source, annotationNode, declaringClassNode, datastoreVar, setTargetDatastoreBody)

                // Add method: @Autowired void setTargetDatastore(Datastore[] datastores)
                Parameter[] setTargetDatastoreParams = params(datastoresParam)
                if( declaringClassNode.getMethod("setTargetDatastore", setTargetDatastoreParams) == null) {
                    MethodNode setTargetDatastoreMethod = declaringClassNode.addMethod("setTargetDatastore", Modifier.PUBLIC, VOID_TYPE, setTargetDatastoreParams, null, setTargetDatastoreBody)

                    // Autowire setTargetDatastore via Spring
                    addAnnotationOrGetExisting(setTargetDatastoreMethod, Autowired)
                            .setMember("required", constX(false))

                    compileMethodStatically(source, setTargetDatastoreMethod)
                }

                // Add method:
                // protected Datastore getTargetDatastore(String connectionName)
                //    if($targetDatastore != null)
                //      return $targetDatastore.getDatastoreForConnection(connectionName)
                //    else
                //      return GormEnhancer.findSingleDatastore().getDatastoreForConnection(connectionName)



                Parameter[] getTargetDatastoreParams = params(connectionNameParam)

                if(declaringClassNode.getMethod(METHOD_GET_TARGET_DATASTORE, getTargetDatastoreParams) == null) {
                    MethodNode mn = declaringClassNode.addMethod(METHOD_GET_TARGET_DATASTORE, Modifier.PROTECTED, datastoreType, getTargetDatastoreParams, null,
                            ifElseS(notNullX(datastoreFieldVar),
                                    returnS( callX( datastoreFieldVar, METHOD_GET_DATASTORE_FOR_CONNECTION, varX(connectionNameParam) ) ),
                                    returnS(datastoreLookupCall)
                            ))
                    if(!isSpockTest) {
                        compileMethodStatically(source, mn)
                    }
                }
                if(declaringClassNode.getMethod(METHOD_GET_TARGET_DATASTORE, ZERO_PARAMETERS) == null) {
                    MethodNode mn = declaringClassNode.addMethod(METHOD_GET_TARGET_DATASTORE, Modifier.PROTECTED,  datastoreType, ZERO_PARAMETERS, null,
                            ifElseS( notNullX(datastoreFieldVar ),
                                    returnS(datastoreFieldVar),
                                    returnS(datastoreLookupDefaultCall))
                    )

                    if(!isSpockTest) {
                        compileMethodStatically(source, mn)
                    }

                }
            }

        }



    }

    protected void weaveSetTargetDatastoreBody(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode, Expression datastoreVar, BlockStatement setTargetDatastoreBody) {
        // no-op
    }

}
