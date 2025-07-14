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

import grails.gorm.multitenancy.Tenants
import grails.plugin.cache.GrailsCacheKeyGenerator
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.trait.TraitComposer
import org.grails.datastore.gorm.multitenancy.transform.TenantTransform
import org.grails.datastore.gorm.transform.AbstractMethodDecoratingTransformation
import org.grails.datastore.gorm.transform.AbstractTraitApplyingGormASTTransformation
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.plugin.cache.GrailsCacheManagerAware
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

import static org.codehaus.groovy.ast.ClassHelper.CLOSURE_TYPE
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE
import static org.codehaus.groovy.ast.ClassHelper.int_TYPE
import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX
import static org.codehaus.groovy.ast.tools.GeneralUtils.param
import static org.codehaus.groovy.ast.tools.GeneralUtils.params
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.callD

/**
 * Abstract implementation for implementers of cache annotations
 *
 * @author Graeme Rocher
 * @author Jeff Brown
 */
@CompileStatic
abstract class AbstractCacheTransformation extends AbstractMethodDecoratingTransformation {

    public static final String GRAILS_CACHE_MANAGER_PROPERTY_NAME = 'grailsCacheManager'
    public static final String CACHE_KEY_LOCAL_VARIABLE_NAME = '$_cache_cacheKey'
    public static final String METHOD_PARAMETER_MAP_LOCAL_VARIABLE_NAME = '$_method_parameter_map'
    public static final String CACHE_VARIABLE_LOCAL_VARIABLE_NAME = '$_cache_cacheVariable'
    public static final String GRAILS_CACHE_KEY_GENERATOR_PROPERTY_NAME = 'customCacheKeyGenerator'

    private static final Object APPLIED_MARKER = new Object()
    public static final String METHOD_PREFIX = '$_cache_'
    private static final ClassNode GRAILS_CACHE_KEY_GENERATOR_CLASS_NODE = make(GrailsCacheKeyGenerator)

    private static final MethodNode GENERATE_FROM_CLOSURE_METHOD = GRAILS_CACHE_KEY_GENERATOR_CLASS_NODE.getMethod('generate', params(
                                                                                                    param(STRING_TYPE, 'className'),
                                                                                                    param(STRING_TYPE, 'methodName'),
                                                                                                    param(int_TYPE, 'objHashCode'),
                                                                                                    param(CLOSURE_TYPE, 'keyGenerator')))
    private static final MethodNode GENERATE_FROM_PARAMETERS_METHOD = GRAILS_CACHE_KEY_GENERATOR_CLASS_NODE.getMethod('generate', params(
                                                                                                    param(STRING_TYPE, 'className'),
                                                                                                    param(STRING_TYPE, 'methodName'),
                                                                                                    param(int_TYPE, 'objHashCode'),
                                                                                                    param(MAP_TYPE, 'methodParams')))
    private static final ClassNode CACHE_MANAGER_CLASS_NODE = make(CacheManager)
    private static final MethodNode GET_CACHE_METHOD_NODE = CACHE_MANAGER_CLASS_NODE.getMethod('getCache', [new Parameter(STRING_TYPE, 'name')] as Parameter[])
    private static final MethodNode MAP_PUT_METHOD = MAP_TYPE.getMethod('put', [new Parameter(OBJECT_TYPE, 'key'), new Parameter(OBJECT_TYPE, 'value')] as Parameter[])
    public static final String CACHE_ORIGINAL_METHOD_RETURN_VALUE_LOCAL_VARIABLE_NAME = '$_cache_originalMethodReturnValue'

    @Override
    protected String getRenamedMethodPrefix() {
        return METHOD_PREFIX
    }

    @Override
    protected void enhanceClassNode(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        if(!classNode.getAllInterfaces().contains(make(GrailsCacheManagerAware))) {
            AbstractTraitApplyingGormASTTransformation.weaveTraitWithGenerics(classNode, GrailsCacheManagerAware)
            if (compilationUnit != null) {
                TraitComposer.doExtendTraits(classNode, sourceUnit, compilationUnit)
            }
        }
    }

    protected VariableExpression declareAndInitializeParameterValueMap(AnnotationNode annotationNode, MethodNode methodToCache, BlockStatement codeBlock) {
        if(annotationNode.getMember("key") instanceof ClosureExpression) {
            // if a key generator is specified don't do anything
            return null
        }

        VariableExpression parameterMapVar = varX(METHOD_PARAMETER_MAP_LOCAL_VARIABLE_NAME, MAP_TYPE)
        Statement parameterMapDec = declS(parameterMapVar, new MapExpression())
        codeBlock.addStatement(
            parameterMapDec
        )
        Parameter[] methodParameters = methodToCache.getParameters()
        if (methodParameters) {
            MethodNode mapPutMethod = MAP_PUT_METHOD
            for (Parameter p : methodParameters) {
                String parameterName = p.name
                ArgumentListExpression putArgs = args(
                    constX(parameterName),
                    varX(parameterName)
                )
                MethodCallExpression mce = callX(parameterMapVar, 'put', putArgs)
                mce.methodTarget = mapPutMethod
                codeBlock.addStatement(new ExpressionStatement(mce))
            }

            if(TenantTransform.hasTenantAnnotation(methodToCache)) {
                ArgumentListExpression putArgs = args(
                        constX(GormProperties.TENANT_IDENTITY),
                        callD(classX(Tenants), "currentId")
                )
                MethodCallExpression mce = callX(parameterMapVar, 'put', putArgs)
                mce.methodTarget = mapPutMethod
                codeBlock.addStatement(new ExpressionStatement(mce))
            }
        }
        return parameterMapVar
    }

    protected VariableExpression declareCache(AnnotationNode annotationNode, VariableExpression cacheManagerVariableExpression, BlockStatement cacheBlock) {
        VariableExpression cacheVariableExpression = varX(CACHE_VARIABLE_LOCAL_VARIABLE_NAME, make(Cache))
        Expression cacheNameExpression = (ConstantExpression) annotationNode.getMember('value')

        MethodCallExpression getCacheMethodCallExpression = callX(cacheManagerVariableExpression, 'getCache', args(cacheNameExpression))
        getCacheMethodCallExpression.methodTarget = GET_CACHE_METHOD_NODE

        cacheBlock.addStatement(
            declS(cacheVariableExpression, getCacheMethodCallExpression)
        )
        return cacheVariableExpression
    }

    protected VariableExpression declareCacheKey(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, BlockStatement cacheBlock) {
        ArgumentListExpression createKeyArgs = args(
                constX(classNode.getName()),
                constX(methodNode.getName()),
                callX(varX('this'), 'hashCode', new ArgumentListExpression())
        )

        Expression keyGenMember = annotationNode.getMember('key')
        MethodNode generateMethod

        if (keyGenMember instanceof ClosureExpression) {
            ClosureExpression closureExpression = (ClosureExpression) keyGenMember
            annotationNode.members.remove('key')
            makeClosureParameterAware(sourceUnit, methodNode, closureExpression)

            generateMethod = GENERATE_FROM_CLOSURE_METHOD
            createKeyArgs.addExpression(keyGenMember)
        } else {
            generateMethod = GENERATE_FROM_PARAMETERS_METHOD
            createKeyArgs.addExpression(varX(METHOD_PARAMETER_MAP_LOCAL_VARIABLE_NAME))
        }

        // customCacheKeyGenerator.generate(className, methodName, hashCode, map)
        MethodCallExpression cacheKeyExpression = callX(varX(GRAILS_CACHE_KEY_GENERATOR_PROPERTY_NAME), generateMethod.name, createKeyArgs)
        cacheKeyExpression.methodTarget = generateMethod

        // def $_cache_cacheKey = .. // generated key
        VariableExpression cacheKeyVariableExpression = varX(CACHE_KEY_LOCAL_VARIABLE_NAME)
        cacheBlock.addStatement(
            declS(cacheKeyVariableExpression, cacheKeyExpression)
        )
        return cacheKeyVariableExpression
    }

    @Override
    protected Object getAppliedMarker() {
        return APPLIED_MARKER
    }

    protected void makeClosureParameterAware(SourceUnit sourceUnit, MethodNode method, ClosureExpression closureExpression) {
        VariableScope variableScope = closureExpression.variableScope
        for (p in method.parameters) {
            if (variableScope.isReferencedClassVariable(p.name)) {
                variableScope.removeReferencedClassVariable(p.name)
            }
            variableScope.putDeclaredVariable(p)
            variableScope.putReferencedLocalVariable(p)
            p.setClosureSharedVariable(true)
        }
        new ClassCodeExpressionTransformer() {
            @Override
            Expression transform(Expression exp) {
                if (exp instanceof VariableExpression) {
                    Variable var = ((VariableExpression) exp).accessedVariable
                    if (var instanceof DynamicVariable) {
                        Variable ref = variableScope.getDeclaredVariable(var.name)
                        if (ref != null) {
                            def newExpr = new VariableExpression(ref)
                            newExpr.setClosureSharedVariable(true)
                            newExpr.setAccessedVariable(ref)

                            return newExpr
                        }
                    }
                }
                return super.transform(exp)
            }

            @Override
            protected SourceUnit getSourceUnit() {
                return sourceUnit
            }
        }.visitClosureExpression(closureExpression)
    }

    protected void handleCacheCondition(SourceUnit sourceUnit, AnnotationNode annotationNode, MethodNode methodNode, MethodCallExpression originalMethodCallExpr, BlockStatement newMethodBody) {
        Expression conditionMember = annotationNode.getMember('condition')
        if (conditionMember instanceof ClosureExpression) {
            ClosureExpression closureExpression = (ClosureExpression) conditionMember
            makeClosureParameterAware(sourceUnit, methodNode, closureExpression)
            // Adds check whether caching should happen
            // if(!condition.call()) {
            //    return originalMethodCall.call()

            Statement ifShouldCacheMethodCallStatement = ifS(
                    notX(callX(conditionMember, "call")),
                    returnS(originalMethodCallExpr)
            )
            newMethodBody.addStatement(ifShouldCacheMethodCallStatement)
            annotationNode.members.remove('condition')
        }
    }
}
