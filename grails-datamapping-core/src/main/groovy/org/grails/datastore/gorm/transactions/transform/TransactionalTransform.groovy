/* Copyright (C) 2017-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.transactions.transform

import grails.gorm.transactions.GrailsTransactionTemplate
import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.multitenancy.transform.TenantTransform
import org.grails.datastore.gorm.transform.AbstractDatastoreMethodDecoratingTransformation
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute

import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.*
import static org.grails.datastore.mapping.reflect.AstUtils.*

/**
 * <p>This AST transform reads the {@link Transactional} annotation and transforms method calls by
 * wrapping the body of the method in an execution of {@link GrailsTransactionTemplate}.</p>
 *
 * <p>In other words given the following class:</p>
 *
 *
 * <pre>
 * class FooService {
 *   {@code @Transactional}
 *   void updateFoo() {
 *       ...
 *   }
 * }
 * </pre>
 *
 *
 * <p>The resulting byte code produced will be (more or less):</p>
 *
 * <pre>
 * class FooService {
 *   PlatformTransactionManager $transactionManager
 *
 *   PlatformTransactionManager getTransactionManager() { $transactionManager }
 *
 *   void updateFoo() {
 *       GrailsTransactionTemplate template = new GrailsTransactionTemplate(getTransactionManager())
 *       template.execute { TransactionStatus status ->
 *           $tt_updateFoo(status)
 *       }
 *   }
 *
 *   private void $tt_updateFoo(TransactionStatus status) {
 *       ...
 *   }
 * }
 * </pre>
 *
 * <p>
 *     The body of the method is moved to a new method prefixed with "$tt_" and which receives the arguments of the method and the TransactionStatus object
 * </p>
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class TransactionalTransform extends AbstractDatastoreMethodDecoratingTransformation {
    private static final Set<String> ANNOTATION_NAME_EXCLUDES = new HashSet<String>([Transactional.class.getName(), "grails.transaction.Rollback", Rollback.class.getName(), NotTransactional.class.getName(), "grails.transaction.NotTransactional", "grails.gorm.transactions.ReadOnly"])
    public static final ClassNode MY_TYPE = new ClassNode(Transactional)
    public static final ClassNode READ_ONLY_TYPE = new ClassNode(ReadOnly)
    private static final String PROPERTY_TRANSACTION_MANAGER = "transactionManager"
    private static final String METHOD_EXECUTE = "execute"
    private static final Object APPLIED_MARKER = new Object()
    private static final String SET_TRANSACTION_MANAGER = "setTransactionManager"
    private static final Set<String> VALID_ANNOTATION_NAMES = Collections.unmodifiableSet(
        new HashSet<String>([Transactional.simpleName, Rollback.simpleName, ReadOnly.simpleName])
    )
    public static final String GET_TRANSACTION_MANAGER_METHOD = "getTransactionManager"

    public static final String RENAMED_METHOD_PREFIX = '$tt__'

    /**
     * Finds the transactional annotation for the given method node
     *
     * @param methodNode The method node
     * @return The annotation or null
     */
    static AnnotationNode findTransactionalAnnotation(MethodNode methodNode) {
        AnnotationNode ann = findAnnotation(methodNode, Transactional)
        if (ann != null) {
            return ann
        }
        ann = findAnnotation(methodNode, ReadOnly)
        if (ann != null) {
            return ann
        }
        ann = findAnnotation(methodNode.getDeclaringClass(), Transactional)
        if (ann != null) {
            return ann
        }
        ann = findAnnotation(methodNode.getDeclaringClass(), ReadOnly)
        return ann
    }

    @Override
    protected boolean isValidAnnotation(AnnotationNode annotationNode, AnnotatedNode classNode) {
        return VALID_ANNOTATION_NAMES.contains( annotationNode.classNode.nameWithoutPackage )
    }

    @Override
    protected ClassNode getAnnotationType() {
        return MY_TYPE
    }

    @Override
    protected Object getAppliedMarker() {
        return APPLIED_MARKER
    }

    @Override
    protected Parameter[] prepareNewMethodParameters(MethodNode methodNode, Map<String, ClassNode> genericsSpec, ClassNode classNode = null) {
        final Parameter transactionStatusParameter = param(make(TransactionStatus), "transactionStatus")
        Parameter[] parameters = methodNode.getParameters()
        Parameter[] newParameters = parameters.length > 0 ? (copyParameters(((parameters as List) + [transactionStatusParameter]) as Parameter[], genericsSpec)) : [transactionStatusParameter] as Parameter[]
        return newParameters
    }

    @Override
    protected MethodNode weaveNewMethod(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        super.weaveNewMethod(sourceUnit, annotationNode, classNode, methodNode, genericsSpec)
    }

    @Override
    protected void weaveClassNode(SourceUnit source, AnnotationNode annotationNode, ClassNode classNode) {
        super.weaveClassNode(source, annotationNode, classNode)
    }

    @Override
    protected void enhanceClassNode(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode) {
        weaveTransactionManagerAware(sourceUnit, annotationNode, declaringClassNode)
        super.enhanceClassNode(source, annotationNode, declaringClassNode)
    }

    @Override
    protected void weaveTestSetupMethod(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        def requiresNewTransaction = new AnnotationNode(annotationNode.classNode)
        requiresNewTransaction.addMember("propagation", propX( classX(Propagation), "REQUIRES_NEW") )
        weaveNewMethod(sourceUnit, requiresNewTransaction, classNode, methodNode, genericsSpec)
    }

    @Override
    protected void weaveSetTargetDatastoreBody(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode, Expression datastoreVar, BlockStatement setTargetDatastoreBody) {
        String transactionManagerFieldName = '$' + PROPERTY_TRANSACTION_MANAGER
        VariableExpression transactionManagerPropertyExpr = varX(transactionManagerFieldName)
        Statement assignConditional = ifS(notNullX(datastoreVar),
                assignS(transactionManagerPropertyExpr, callX(castX(make(TransactionCapableDatastore), datastoreVar), GET_TRANSACTION_MANAGER_METHOD)))
        setTargetDatastoreBody.addStatement(assignConditional)

    }

    protected void weaveTransactionManagerAware(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode) {
        if ( declaringClassNode.getNodeMetaData(APPLIED_MARKER) == APPLIED_MARKER ) {
            return
        }

        Expression connectionName = annotationNode.getMember("connection")
        if( connectionName == null ) {
            connectionName = annotationNode.getMember("value")
        }
        boolean hasDataSourceProperty = connectionName != null

        //add the transactionManager property
        if (!hasOrInheritsProperty(declaringClassNode, PROPERTY_TRANSACTION_MANAGER) ) {

            ClassNode transactionManagerClassNode = make(PlatformTransactionManager)

            // build a static lookup in the case of no property set
            ClassExpression gormEnhancerExpr = classX(GormEnhancer)
            Expression val = annotationNode.getMember("datastore")
            MethodCallExpression transactionManagerLookupExpr
            if(val instanceof ClassExpression) {
                transactionManagerLookupExpr = hasDataSourceProperty ? callX(gormEnhancerExpr, "findTransactionManager", args(val, connectionName)) : callX(gormEnhancerExpr, "findTransactionManager", val)
                Parameter typeParameter = param(CLASS_Type, "type")
                Parameter[] params = hasDataSourceProperty ? params(typeParameter, param(STRING_TYPE, "connectionName")) : params(typeParameter)

                transactionManagerLookupExpr.setMethodTarget(
                        gormEnhancerExpr.getType().getDeclaredMethod("findTransactionManager", params )
                )
            }
            else {
                transactionManagerLookupExpr = hasDataSourceProperty ? callX(gormEnhancerExpr, "findSingleTransactionManager", connectionName) : callX(gormEnhancerExpr, "findSingleTransactionManager")
                Parameter[] params = hasDataSourceProperty ? params(param(STRING_TYPE, "connectionName")) : ZERO_PARAMETERS
                transactionManagerLookupExpr.setMethodTarget(
                        gormEnhancerExpr.getType().getDeclaredMethod("findSingleTransactionManager", params )
                )
            }

            // simply logic for classes that implement Service
            if(implementsInterface(declaringClassNode, "org.grails.datastore.mapping.services.Service")) {
                // Add Method: PlatformTransactionManager getTransactionManager()
                // if(datastore != null)
                //     return datastore.transactionManager
                // else
                //     return GormEnhancer.findSingleTransactionManager()
                ClassNode transactionCapableDatastore = make(TransactionCapableDatastore)
                Expression datastoreVar = castX(transactionCapableDatastore, varX("datastore") )
                Expression datastoreLookupExpr = datastoreVar
                if(hasDataSourceProperty) {
                    datastoreLookupExpr = callD(castX(make(MultipleConnectionSourceCapableDatastore), datastoreVar), "getDatastoreForConnection", connectionName )
                }
                Statement ifElse = ifElseS(
                        notNullX(datastoreVar),
                        returnS( propX( castX(transactionCapableDatastore, datastoreLookupExpr), PROPERTY_TRANSACTION_MANAGER) ),
                        returnS( transactionManagerLookupExpr )
                )

                declaringClassNode.addMethod(GET_TRANSACTION_MANAGER_METHOD,
                        Modifier.PUBLIC,
                        transactionManagerClassNode,
                        ZERO_PARAMETERS, null,
                        ifElse)

            }
            else {
                /// Add field: PlatformTransactionManager $transactionManager
                String transactionManagerFieldName = '$' + PROPERTY_TRANSACTION_MANAGER
                FieldNode transactionManagerField = declaringClassNode.addField(transactionManagerFieldName, Modifier.PROTECTED, transactionManagerClassNode, null)


                VariableExpression transactionManagerPropertyExpr = varX(transactionManagerField)
                BlockStatement getterBody = block()

                // this is a hacky workaround that ensures the transaction manager is also set on the spock shared instance which seems to differ for
                // some reason
                if(isSubclassOf(declaringClassNode, "spock.lang.Specification")) {
                    getterBody.addStatement(
                            stmt(
                                    callX( propX( propX( varThis(), "specificationContext"), "sharedInstance"),
                                            SET_TRANSACTION_MANAGER,
                                            transactionManagerPropertyExpr)
                            )
                    )
                }


                // Prepare the getTransactionManager() method body
                // if($transactionManager != null)
                //     return $transactionManager
                // else
                //     return GormEnhancer.findSingleTransactionManager()
                Statement ifElse = ifElseS(
                        notNullX( transactionManagerPropertyExpr ),
                        returnS( transactionManagerPropertyExpr ),
                        returnS(transactionManagerLookupExpr)
                )

                getterBody.addStatement( ifElse )

                // Add Method: PlatformTransactionManager getTransactionManager()
                declaringClassNode.addMethod(GET_TRANSACTION_MANAGER_METHOD,
                        Modifier.PUBLIC,
                        transactionManagerClassNode,
                        ZERO_PARAMETERS, null,
                        getterBody)

                // Prepare setter parameters
                Parameter p = param(transactionManagerClassNode, PROPERTY_TRANSACTION_MANAGER)
                Parameter[] parameters = params(p)
                if(declaringClassNode.getMethod(SET_TRANSACTION_MANAGER, parameters) == null) {
                    Statement setterBody = assignS(transactionManagerPropertyExpr, varX(p))

                    // Add Setter Method: void setTransactionManager(PlatformTransactionManager transactionManager)
                    declaringClassNode.addMethod(SET_TRANSACTION_MANAGER,
                            Modifier.PUBLIC,
                            VOID_TYPE,
                            parameters,
                            null,
                            setterBody)
                }
            }


        }
    }

    MethodCallExpression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, MethodCallExpression originalMethodCall, BlockStatement newMethodBody) {
        String executeMethodName = isTestSetupOrCleanup(classNode, methodNode) ? METHOD_EXECUTE : getTransactionTemplateMethodName()
        // CustomizableRollbackTransactionAttribute $transactionAttribute = new CustomizableRollbackTransactionAttribute()
        final ClassNode transactionAttributeClassNode = make(CustomizableRollbackTransactionAttribute)
        final VariableExpression transactionAttributeVar = varX('$transactionAttribute', transactionAttributeClassNode)
        newMethodBody.addStatement(
            declS(transactionAttributeVar, ctorX(transactionAttributeClassNode, ZERO_ARGUMENTS))
        )

        // apply @Transaction attributes to properties of $transactionAttribute
        applyTransactionalAttributeSettings(annotationNode, transactionAttributeVar, newMethodBody, classNode, methodNode)

        boolean isMultiTenant = TenantTransform.hasTenantAnnotation(methodNode)

        Expression connectionName = annotationNode.getMember("connection")
        if( connectionName == null ) {
            connectionName = annotationNode.getMember("value")
        }
        if(connectionName == null) {
            if(isMultiTenant) {
                connectionName = varX("tenantId")
            }
        }
        final boolean hasDataSourceProperty = connectionName != null

        // $transactionManager = connection != null ? getTargetDatastore(connection).getTransactionManager() : getTransactionManager()
        Expression transactionManagerExpression
        if(isMultiTenant && hasDataSourceProperty) {
            Expression targetDatastoreExpr = castX( make(MultiTenantCapableDatastore), callThisD(classNode, "getTargetDatastore", ZERO_ARGUMENTS) )
            targetDatastoreExpr = castX( make(TransactionCapableDatastore), callX( targetDatastoreExpr, "getDatastoreForTenantId", connectionName))
            transactionManagerExpression = castX( make(PlatformTransactionManager), propX(targetDatastoreExpr, PROPERTY_TRANSACTION_MANAGER) )

        }
        else if(hasDataSourceProperty) {
            // callX(varX("this"), "getTargetDatastore", connectionName)
            def targetDatastoreExpr = castX( make(TransactionCapableDatastore), callThisD(classNode, "getTargetDatastore", connectionName) )
            transactionManagerExpression = castX( make(PlatformTransactionManager), propX(targetDatastoreExpr, PROPERTY_TRANSACTION_MANAGER) )
        }
        else {
            transactionManagerExpression = propX( varX("this"), PROPERTY_TRANSACTION_MANAGER)
        }

        // GrailsTransactionTemplate $transactionTemplate
        //           = new GrailsTransactionTemplate(getTransactionManager(), $transactionAttribute )
        final ClassNode transactionTemplateClassNode = make(GrailsTransactionTemplate)
        final VariableExpression transactionTemplateVar = varX('$transactionTemplate', transactionTemplateClassNode)

        newMethodBody.addStatement(
            declS(
                transactionTemplateVar,
                ctorX(transactionTemplateClassNode, args(
                    transactionManagerExpression,
                    transactionAttributeVar
                ))
            )
        )

        // Wrap original method in closure that executes within the context of a transaction
        // return $transactionTemplate.execute { TransactionStatus transactionStatus ->
        //       return $tt_myMethod(transactionStatus)
        // }
        Parameter transactionStatusParam = param(make(TransactionStatus), "transactionStatus")
        Parameter[] parameters = params(transactionStatusParam)
        return makeDelegatingClosureCall(transactionTemplateVar, executeMethodName, parameters, originalMethodCall, methodNode.getVariableScope() )
    }

    protected String getTransactionTemplateMethodName() {
        return "execute"
    }

    protected applyTransactionalAttributeSettings(AnnotationNode annotationNode, VariableExpression transactionAttributeVar, BlockStatement methodBody, ClassNode classNode, MethodNode methodNode) {
        final ClassNode rollbackRuleAttributeClassNode = make(RollbackRuleAttribute)
        final ClassNode noRollbackRuleAttributeClassNode = make(NoRollbackRuleAttribute)
        final Map<String, Expression> members = annotationNode.getMembers()
        if(READ_ONLY_TYPE.equals(annotationNode.classNode)) {
            methodBody.addStatement(
                assignS(propX(transactionAttributeVar, "readOnly"), ConstantExpression.TRUE)
            )
        }

        members.each { String name, Expression expr ->
            if (name == 'rollbackFor' || name == 'rollbackForClassName' || name == 'noRollbackFor' || name == 'noRollbackForClassName') {
                final targetClassNode = (name == 'rollbackFor' || name == 'rollbackForClassName') ? rollbackRuleAttributeClassNode : noRollbackRuleAttributeClassNode
                name = 'rollbackRules'
                if (expr instanceof ListExpression) {
                    for(exprItem in ((ListExpression)expr).expressions) {
                        appendRuleElement(methodBody, transactionAttributeVar, name, ctorX(targetClassNode, exprItem))
                    }
                } else {
                    appendRuleElement(methodBody, transactionAttributeVar, name, ctorX(targetClassNode, expr))
                }
            } else {
                if (name == 'isolation') {
                    name = 'isolationLevel'
                    expr = callX(expr, "value", ZERO_ARGUMENTS)
                } else if (name == 'propagation') {
                    name = 'propagationBehavior'
                    expr = callX(expr, "value", ZERO_ARGUMENTS)
                }

                if(name != 'value') {
                    methodBody.addStatement(
                        assignS(propX(transactionAttributeVar, name), expr)
                    )
                }
            }
        }

        final transactionName = classNode.name + '.' + methodNode.name
        methodBody.addStatement(
            assignS(propX(transactionAttributeVar, 'name'), new ConstantExpression(transactionName))
        )
    }

    private void appendRuleElement(BlockStatement methodBody, VariableExpression transactionAttributeVar, String name, Expression expr) {
        final rollbackRuleAttributeClassNode = make(RollbackRuleAttribute)
        ClassNode rollbackRulesListClassNode = nonGeneric(make(List), rollbackRuleAttributeClassNode)
        def getRollbackRules = castX(rollbackRulesListClassNode, buildGetPropertyExpression(transactionAttributeVar, name, transactionAttributeVar.getType()))
        methodBody.addStatement(
            stmt(
                callX(getRollbackRules, 'add', expr)
            )
        )
    }

    @Override
    protected boolean hasExcludedAnnotation(MethodNode md) {
        return super.hasExcludedAnnotation(md) || hasExcludedAnnotation(md, ANNOTATION_NAME_EXCLUDES)
    }

    /**
     * Whether the given method has a transactional annotation
     *
     * @param md The method node
     * @return
     */
    static boolean hasTransactionalAnnotation(AnnotatedNode md) {
        for (AnnotationNode annotation : md.getAnnotations()) {
            if(ANNOTATION_NAME_EXCLUDES.any() { String n -> n == annotation.classNode.name}) {
                return true
            }
        }
        return false
    }

    @Override
    protected String getRenamedMethodPrefix() {
        return RENAMED_METHOD_PREFIX
    }

    @Override
    int priority() {
        GroovyTransformOrder.TRANSACTIONAL_ORDER
    }
}
