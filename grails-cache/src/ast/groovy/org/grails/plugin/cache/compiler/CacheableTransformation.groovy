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
package org.grails.plugin.cache.compiler

import grails.plugin.cache.Cacheable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.core.artefact.ControllerArtefactHandler
import org.springframework.cache.Cache

import static org.codehaus.groovy.ast.ClassHelper.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.*

/**
 * @since 4.0.0
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 */
@Slf4j
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
@CompileStatic
class CacheableTransformation extends AbstractCacheTransformation {

    public static final String CACHE_VALUE_WRAPPER_LOCAL_VARIABLE_NAME = '$_cache_valueWrapper'
    public static final ClassNode ANNOTATION_TYPE = make(Cacheable)

    @Override
    protected ClassNode getAnnotationType() {
        return ANNOTATION_TYPE
    }

    @Override
    protected Expression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, MethodCallExpression originalMethodCallExpr, BlockStatement newMethodBody) {
        boolean isControllerClass = classNode.name.endsWith(ControllerArtefactHandler.TYPE)
        if(isControllerClass) {
            log.warn("@Cacheable is not supported on controller methods. Ignoring method: ${methodNode.name}")
            return originalMethodCallExpr
        }

        VariableExpression cacheManagerVariableExpression = varX(GRAILS_CACHE_MANAGER_PROPERTY_NAME)
        handleCacheCondition(sourceUnit, annotationNode,  methodNode, originalMethodCallExpr, newMethodBody)

        BlockStatement cachingBlock = block()

        // Generated logic looks like:
        //
        // Cache $_cache_cacheVariable = this.grailsCacheManager.getCache("...");
        VariableExpression cacheDeclaration = declareCache(annotationNode, cacheManagerVariableExpression, cachingBlock)

        // def $_method_parameter_map = [name:value]
        declareAndInitializeParameterValueMap(annotationNode, methodNode, cachingBlock)

        // def $_cache_cacheKey = customCacheKeyGenerator.generate(className, methodName, hashCode, $_method_parameter_map)
        VariableExpression cacheKeyDeclaration = declareCacheKey(sourceUnit, annotationNode, classNode, methodNode , cachingBlock)


        // ValueWrapper $_cache_valueWrapper = $_cache_cacheVariable.get($_cache_cacheKey);
        VariableExpression cacheValueWrapper = varX(CACHE_VALUE_WRAPPER_LOCAL_VARIABLE_NAME, make(Cache.ValueWrapper))
        cachingBlock.addStatement(
            declS(cacheValueWrapper, callD(cacheDeclaration, "get", cacheKeyDeclaration))
        )

        // if($_cache_valueWrapper != null) {
        //    return $_cache_valueWrapper.get();
        // } else {
        //    Object $_cache_originalMethodReturnValue = this.$_cache_originalMethod();
        //    $_cache_cacheVariable.put($_cache_cacheKey, $_cache_originalMethodReturnValue);
        //    return $_cache_originalMethodReturnValue;
        // }
        VariableExpression originalValueExpr = varX(CACHE_ORIGINAL_METHOD_RETURN_VALUE_LOCAL_VARIABLE_NAME)
        cachingBlock.addStatement(
            ifElseS(notNullX(cacheValueWrapper),
                returnS( callD(cacheValueWrapper, "get")),
                block(
                    declS(originalValueExpr, originalMethodCallExpr),
                    stmt(callD(cacheDeclaration,"put", args(cacheKeyDeclaration, originalValueExpr))),
                    returnS(originalValueExpr)
                )
            )
        )

        newMethodBody.addStatement(
            // if(grailsCacheManager != null)
            ifS(notNullX(varX(GRAILS_CACHE_MANAGER_PROPERTY_NAME)),
                    cachingBlock
            )
        )
        return originalMethodCallExpr
    }

}
