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
package org.grails.datastore.gorm.multitenancy.transform

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant
import grails.gorm.multitenancy.TenantService
import grails.gorm.multitenancy.WithoutTenant
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.gorm.transform.AbstractDatastoreMethodDecoratingTransformation
import org.grails.datastore.mapping.core.Ordered
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.datastore.mapping.services.ServiceRegistry

import static org.codehaus.groovy.ast.ClassHelper.CLOSURE_TYPE
import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.callD
import static org.grails.datastore.mapping.reflect.AstUtils.copyParameters
import static org.grails.datastore.mapping.reflect.AstUtils.varThis

/**
 * Implementation of {@link grails.gorm.multitenancy.Tenant}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class TenantTransform extends AbstractDatastoreMethodDecoratingTransformation implements Ordered {
    private static final Object APPLIED_MARKER = new Object()
    private static final ClassExpression CURRENT_TENANT_ANNOTATION_TYPE_EXPR = classX(CurrentTenant)
    private static final ClassExpression TENANT_ANNOTATION_TYPE_EXPR = classX(Tenant)
    private static final ClassExpression WITHOUT_TENANT_ANNOTATION_TYPE_EXPR = classX(WithoutTenant)

    public static final ClassNode TENANT_ANNOTATION_TYPE = TENANT_ANNOTATION_TYPE_EXPR.getType()
    public  static final ClassNode CURRENT_TENANT_ANNOTATION_TYPE = CURRENT_TENANT_ANNOTATION_TYPE_EXPR.getType()
    public  static final ClassNode WITHOUT_TENANT_ANNOTATION_TYPE = WITHOUT_TENANT_ANNOTATION_TYPE_EXPR.getType()

    public static final String RENAMED_METHOD_PREFIX = '$mt__'
    public static final String VAR_TENANT_ID = "tenantId"
    /**
     * The position of the transform. Before the transactional transform
     */
    public static final int POSITION = TransactionalTransform.POSITION - 100

    private static final Parameter[] N0_PARAMETER = null

    @Override
    protected String getRenamedMethodPrefix() {
        return RENAMED_METHOD_PREFIX
    }

    @Override
    MethodCallExpression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, MethodCallExpression originalMethodCallExpr, BlockStatement newMethodBody) {
        ClassNode tenantServiceClassNode = make(TenantService)
        VariableScope variableScope = methodNode.getVariableScope()
        VariableExpression tenantServiceVar = varX('$tenantService', tenantServiceClassNode)
        variableScope.putDeclaredVariable( tenantServiceVar )
        newMethodBody.addStatement(
            declS(tenantServiceVar, callD(ServiceRegistry, "targetDatastore", "getService", classX(tenantServiceClassNode) ) )
        )

        ClassNode serializableClassNode = make(Serializable)
        ClassNode annotationClassNode = annotationNode.classNode
        if (CURRENT_TENANT_ANNOTATION_TYPE.equals(annotationClassNode)) {
            return makeDelegatingClosureCall(tenantServiceVar, "withCurrent", params(param(serializableClassNode, VAR_TENANT_ID)), originalMethodCallExpr, variableScope)
        } else if (WITHOUT_TENANT_ANNOTATION_TYPE.equals(annotationClassNode)) {
            return makeDelegatingClosureCall(tenantServiceVar, "withoutId", N0_PARAMETER, originalMethodCallExpr, variableScope)
        } else {
            // must be @Tenant
            Expression annValue = annotationNode.getMember("value")
            if(annValue instanceof ClosureExpression) {
                VariableExpression closureVar = varX('$tenantResolver', CLOSURE_TYPE)
                VariableExpression tenantIdVar = varX('$tenantId', serializableClassNode)
                tenantIdVar.setClosureSharedVariable(true)
                variableScope.putDeclaredVariable( closureVar )
                variableScope.putReferencedLocalVariable( tenantIdVar )
                variableScope.putDeclaredVariable( tenantIdVar )
                // Generates:
                // Closure $tenantResolver = ...
                // $tenantResolver = $tenantResolver.clone()
                // $tenantResolver.setDelegate(this)
                // Serializable $tenantId = (Serializable)$tenantResolver.call()
                // if($tenantId == null) throw new TenantNotFoundException(..)
                newMethodBody.addStatement  declS( closureVar, annValue)
                newMethodBody.addStatement  assignS( closureVar, callD( closureVar, "clone"))
                newMethodBody.addStatement  stmt( callD( closureVar, "setDelegate", varThis() ) )
                newMethodBody.addStatement  declS( tenantIdVar, castX( serializableClassNode, callD( closureVar, "call") ))
                newMethodBody.addStatement ifS( equalsNullX(tenantIdVar),
                    throwS( ctorX( make(TenantNotFoundException), constX("Tenant id resolved from @Tenant is null")) )
                )
                return makeDelegatingClosureCall( tenantServiceVar, "withId", args(tenantIdVar), params( param(serializableClassNode, VAR_TENANT_ID)), originalMethodCallExpr, variableScope)
            }
            else {
                addError("@Tenant value should be a closure", annotationNode)
                return makeDelegatingClosureCall( tenantServiceVar, "withCurrent", params( param(serializableClassNode, VAR_TENANT_ID)), originalMethodCallExpr, variableScope)
            }
        }
    }

    @Override
    protected Parameter[] prepareNewMethodParameters(MethodNode methodNode, Map<String, ClassNode> genericsSpec, ClassNode classNode = null) {
        if(methodNode.getAnnotations(WITHOUT_TENANT_ANNOTATION_TYPE).isEmpty() && (!classNode || classNode.getAnnotations(WITHOUT_TENANT_ANNOTATION_TYPE).isEmpty())) {
            final Parameter tenantIdParameter = param(make(Serializable), VAR_TENANT_ID)
            Parameter[] parameters = methodNode.getParameters()
            Parameter[] newParameters = parameters.length > 0 ? (copyParameters(((parameters as List) + [tenantIdParameter]) as Parameter[], genericsSpec)) : [tenantIdParameter] as Parameter[]
            return newParameters
        }
        else {
            return copyParameters(methodNode.getParameters())
        }
    }

    @Override
    protected boolean isValidAnnotation(AnnotationNode annotationNode, AnnotatedNode classNode) {
        ClassNode annotationClassNode = annotationNode.getClassNode()
        TENANT_ANNOTATION_TYPE.equals(annotationClassNode) || CURRENT_TENANT_ANNOTATION_TYPE.equals(annotationClassNode) || WITHOUT_TENANT_ANNOTATION_TYPE.equals(annotationClassNode)
    }

    @Override
    protected ClassNode getAnnotationType() {
        return TENANT_ANNOTATION_TYPE
    }

    @Override
    protected Object getAppliedMarker() {
        return APPLIED_MARKER
    }

    @Override
    int getOrder() {
        return POSITION
    }

    /**
     * Whether the given node is Multi Tenant
     *
     * @param node The node
     * @return True if it is
     */
    static boolean hasTenantAnnotation(AnnotatedNode node) {
        ClassNode classNode
        if(node instanceof MethodNode) {
            if(AstUtils.findAnnotation(node, WithoutTenant)) {
                return false
            }
            classNode = ((MethodNode)node).getDeclaringClass()
        }
        else if(node instanceof ClassNode) {
            classNode = ((ClassNode)node)
        }
        if(classNode != null) {

            for(ann in [CurrentTenant, Tenant]) {
                if(AstUtils.findAnnotation(classNode, ann) || AstUtils.findAnnotation(node, ann)) {
                    return true
                }
            }
        }
        return false
    }
}
