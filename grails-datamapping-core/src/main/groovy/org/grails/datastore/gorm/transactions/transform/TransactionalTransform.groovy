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

import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.AttributeExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute

import grails.gorm.transactions.GrailsTransactionTemplate
import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.multitenancy.transform.TenantTransform
import org.grails.datastore.gorm.transform.AbstractDatastoreMethodDecoratingTransformation
import org.grails.datastore.mapping.transactions.CustomizableRollbackTransactionAttribute
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

import static org.apache.groovy.ast.tools.AnnotatedNodeUtils.markAsGenerated
import static org.codehaus.groovy.ast.ClassHelper.VOID_TYPE
import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifElseS
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS
import static org.codehaus.groovy.ast.tools.GeneralUtils.notNullX
import static org.codehaus.groovy.ast.tools.GeneralUtils.param
import static org.codehaus.groovy.ast.tools.GeneralUtils.params
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_ARGUMENTS
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_PARAMETERS
import static org.grails.datastore.mapping.reflect.AstUtils.copyParameters
import static org.grails.datastore.mapping.reflect.AstUtils.findAnnotation
import static org.grails.datastore.mapping.reflect.AstUtils.implementsInterface
import static org.grails.datastore.mapping.reflect.AstUtils.nonGeneric
import static org.grails.datastore.mapping.reflect.AstUtils.varThis

/**
 * <p>This AST transform reads the {@link Transactional} annotation and transforms method calls by
 * wrapping the body of the method in an execution of {@link GrailsTransactionTemplate}.</p>
 *
 * <p>In other words given the following class:</p>
 *
 *
 * <pre>
 * {@code @Transactional}
 * class MyService {
 *      void saveBook(String title) {
 *           new Book(title:title).save()
 *      }
 * }
 * </pre>
 *
 * <p>The transform will produce:</p>
 *
 * <pre>
 * class MyService {
 *      {@code @Autowired}
 *      PlatformTransactionManager transactionManager
 *
 *      void saveBook(String title) {
 *           transactionManager.execute { TransactionStatus status ->
 *                new Book(title:title).save()
 *           }
 *      }
 * }
 * </pre>
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class TransactionalTransform extends AbstractDatastoreMethodDecoratingTransformation {

    private static final Object APPLIED_MARKER = new Object()

    public static final ClassNode MY_TYPE = make(Transactional)
    public static final ClassNode READ_ONLY_TYPE = make(ReadOnly)
    private static final Set<String> VALID_ANNOTATION_NAMES = Collections.unmodifiableSet(
        new HashSet<String>([Transactional.simpleName, Rollback.simpleName, ReadOnly.simpleName])
    )
    public static final String GET_TRANSACTION_MANAGER_METHOD = 'getTransactionManager'
    public static final String SET_TRANSACTION_MANAGER = 'setTransactionManager'
    public static final String PROPERTY_TRANSACTION_MANAGER = 'transactionManager'
    public static final String METHOD_EXECUTE = 'execute'

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

    /**
     * Whether the given node carries an explicit transaction-related annotation. An explicit
     * {@code @NotTransactional} counts as such a decision: callers use this to decide whether to
     * impose a default transaction (read-only for reads, transactional for writes), and a method
     * the user marked {@code @NotTransactional} must not have a default transaction imposed on it.
     *
     * @param node The node
     * @return True if it does
     */
    static boolean hasTransactionalAnnotation(AnnotatedNode node) {
        if (node instanceof MethodNode) {
            if (findAnnotation(node, NotTransactional)) {
                return true
            }
            return findTransactionalAnnotation((MethodNode) node) != null
        }
        else if (node instanceof ClassNode) {
            for (ann in [Transactional, ReadOnly, Rollback]) {
                if (findAnnotation(node, ann)) {
                    return true
                }
            }
        }
        return false
    }

    @Override
    protected boolean isValidAnnotation(AnnotationNode annotationNode, AnnotatedNode classNode) {
        return VALID_ANNOTATION_NAMES.contains(annotationNode.classNode.nameWithoutPackage)
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
        final Parameter transactionStatusParameter = param(make(TransactionStatus), 'transactionStatus')
        Parameter[] parameters = methodNode.getParameters()
        Parameter[] newParameters = parameters.length > 0 ? (copyParameters(((parameters as List) + [transactionStatusParameter]) as Parameter[], genericsSpec)) : [transactionStatusParameter] as Parameter[]
        return newParameters
    }

    @Override
    protected void enhanceClassNode(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode) {
        weaveTransactionManagerAware(source, annotationNode, declaringClassNode)
        super.enhanceClassNode(source, annotationNode, declaringClassNode)
    }

    @Override
    protected void weaveTestSetupMethod(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        def requiresNewTransaction = new AnnotationNode(annotationNode.classNode)
        requiresNewTransaction.addMember('propagation', propX(classX(Propagation), 'REQUIRES_NEW'))
        weaveNewMethod(sourceUnit, requiresNewTransaction, classNode, methodNode, genericsSpec)
    }

    @Override
    protected void weaveSetTargetDatastoreBody(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode, Expression datastoreVar, BlockStatement setTargetDatastoreBody) {
        String transactionManagerFieldName = '$' + PROPERTY_TRANSACTION_MANAGER
        // Only assign to $transactionManager if the field was declared on this class by weaveTransactionManagerAware().
        // When ServiceTransformation runs first and provides getTransactionManager() as a method,
        // weaveTransactionManagerAware() skips field creation, so assigning it here would cause
        // MissingPropertyException at runtime.
        if (declaringClassNode.getDeclaredField(transactionManagerFieldName) != null) {
            VariableExpression transactionManagerPropertyExpr = varX(transactionManagerFieldName)
            Statement assignConditional = ifS(notNullX(datastoreVar),
                    assignS(transactionManagerPropertyExpr, callX(castX(make(TransactionCapableDatastore), datastoreVar), GET_TRANSACTION_MANAGER_METHOD)))
            setTargetDatastoreBody.addStatement(assignConditional)
        }
    }

    protected void weaveTransactionManagerAware(SourceUnit source, AnnotationNode annotationNode, ClassNode declaringClassNode) {
        if (declaringClassNode.getNodeMetaData(APPLIED_MARKER) == APPLIED_MARKER) {
            return
        }
        if (declaringClassNode.getMethod(GET_TRANSACTION_MANAGER_METHOD, ZERO_PARAMETERS) != null) {
            return
        }

        Expression connectionName = annotationNode.getMember('connection')
        if (connectionName == null) {
            connectionName = annotationNode.getMember('value')
        }
        boolean hasDataSourceProperty = connectionName != null

        ClassNode transactionManagerClassNode = make(PlatformTransactionManager)
        Expression registryExpr = callX(classX(GormRegistry), 'getInstance')

        Expression tmFieldExpr
        FieldNode userField = declaringClassNode.getField(PROPERTY_TRANSACTION_MANAGER)
        if (userField != null) {
            tmFieldExpr = new AttributeExpression(varX('this'), constX(PROPERTY_TRANSACTION_MANAGER))
        } else {
            String transactionManagerFieldName = '$' + PROPERTY_TRANSACTION_MANAGER
            FieldNode tmField = declaringClassNode.getDeclaredField(transactionManagerFieldName)
            if (tmField == null) {
                tmField = declaringClassNode.addField(transactionManagerFieldName, Modifier.PRIVATE, transactionManagerClassNode, null)
                markAsGenerated(declaringClassNode, tmField)
            }
            tmFieldExpr = varX(tmField)
        }

        if (declaringClassNode.getMethod(GET_TRANSACTION_MANAGER_METHOD, ZERO_PARAMETERS) == null) {
            // resolved TM expression for the getter fallback
            Expression transactionManagerLookupExpr
            if (implementsInterface(declaringClassNode, 'org.grails.datastore.mapping.services.Service') ||
                findAnnotation(declaringClassNode, grails.gorm.services.Service) != null) {

                // For services, resolve entirely via static bridge
                if (hasDataSourceProperty) {
                    transactionManagerLookupExpr = callX(registryExpr, 'findTransactionManager', args(classX(nonGeneric(declaringClassNode)), connectionName))
                }
                else {
                    transactionManagerLookupExpr = callX(registryExpr, 'findTransactionManager', args(classX(nonGeneric(declaringClassNode))))
                }
            }
            else {
                // For regular objects, use the shared resolver
                if (hasDataSourceProperty) {
                    transactionManagerLookupExpr = callX(registryExpr, 'findSingleTransactionManager', connectionName)
                }
                else {
                    transactionManagerLookupExpr = callX(registryExpr, 'findSingleTransactionManager')
                }
            }

            BlockStatement getterBody = new BlockStatement()
            ClassNode currentTenantHolderClassNode = make(grails.gorm.multitenancy.CurrentTenantHolder)
            ClassNode connectionSourceClassNode = make(org.grails.datastore.mapping.core.connections.ConnectionSource)
            VariableExpression tenantIdVar = varX('tenantId', make(Serializable))
            getterBody.addStatement(
                declS(tenantIdVar, callX(classX(currentTenantHolderClassNode), 'get'))
            )
            
            BlockStatement ifTenantActiveBody = new BlockStatement()
            VariableExpression tmVar = varX('tm', transactionManagerClassNode)
            ifTenantActiveBody.addStatement(
                declS(tmVar, callX(registryExpr, 'findSingleTransactionManager', callX(tenantIdVar, 'toString')))
            )
            ifTenantActiveBody.addStatement(
                ifS(notNullX(tmVar), returnS(tmVar))
            )
            
            getterBody.addStatement(
                ifS(
                    notNullX(tenantIdVar),
                    ifElseS(
                        callX(callX(tenantIdVar, 'toString'), 'equals', propX(classX(connectionSourceClassNode), 'DEFAULT')),
                        new org.codehaus.groovy.ast.stmt.EmptyStatement(),
                        ifTenantActiveBody
                    )
                )
            )
            
            getterBody.addStatement(
                ifElseS(notNullX(tmFieldExpr), returnS(tmFieldExpr), returnS(transactionManagerLookupExpr))
            )

            MethodNode getterNode = declaringClassNode.addMethod(GET_TRANSACTION_MANAGER_METHOD,
                    Modifier.PUBLIC,
                    transactionManagerClassNode,
                    ZERO_PARAMETERS, null,
                    getterBody)
            markAsGenerated(declaringClassNode, getterNode)
        }

        // Add setter: public void setTransactionManager(PlatformTransactionManager tm)
        Parameter p = param(transactionManagerClassNode, PROPERTY_TRANSACTION_MANAGER)
        if (declaringClassNode.getMethod(SET_TRANSACTION_MANAGER, params(p)) == null) {
            MethodNode setterNode = declaringClassNode.addMethod(SET_TRANSACTION_MANAGER,
                    Modifier.PUBLIC,
                    VOID_TYPE,
                    params(p),
                    null,
                    assignS(tmFieldExpr, varX(p)))
            markAsGenerated(declaringClassNode, setterNode)
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

        boolean isMultiTenant = TenantTransform.hasTenantAnnotation(classNode) || TenantTransform.hasTenantAnnotation(methodNode)
        Expression connectionName = annotationNode.getMember('connection')
        if (connectionName == null) {
            connectionName = annotationNode.getMember('value')
        }
        final boolean hasDataSourceProperty = connectionName != null

        // resolved TM expression
        Expression transactionManagerExpression
        if (connectionName == null) {
            // Use the class-level transaction manager (which supports overrides)
            transactionManagerExpression = callX(varThis(), GET_TRANSACTION_MANAGER_METHOD)
        }
        else if (hasDataSourceProperty) {
            Expression registryExpr = new MethodCallExpression(classX(make(org.grails.datastore.gorm.GormRegistry)), 'getInstance', new org.codehaus.groovy.ast.expr.ArgumentListExpression())
            transactionManagerExpression = castX(make(PlatformTransactionManager), callX(registryExpr, 'findSingleTransactionManager', connectionName))
        }
        else {
            transactionManagerExpression = callX(varThis(), GET_TRANSACTION_MANAGER_METHOD)
        }

        // PlatformTransactionManager $transactionManager = ... resolved TM ...
        final ClassNode transactionManagerClassNode = make(PlatformTransactionManager)
        final VariableExpression transactionManagerVar = varX('$transactionManager', transactionManagerClassNode)
        newMethodBody.addStatement(
            declS(transactionManagerVar, transactionManagerExpression)
        )

        // GrailsTransactionTemplate $transactionTemplate
        //           = new GrailsTransactionTemplate($transactionManager, $transactionAttribute )
        final ClassNode transactionTemplateClassNode = make(GrailsTransactionTemplate)
        final VariableExpression transactionTemplateVar = varX('$transactionTemplate', transactionTemplateClassNode)

        newMethodBody.addStatement(
            declS(
                transactionTemplateVar,
                ctorX(transactionTemplateClassNode, args(
                    transactionManagerVar,
                    transactionAttributeVar
                ))
            )
        )

        // Wrap original method in closure that executes within the context of a transaction
        // return $transactionTemplate.execute { TransactionStatus transactionStatus ->
        //       return $tt_myMethod(transactionStatus)
        // }
        Parameter transactionStatusParam = param(make(TransactionStatus), 'transactionStatus')
        Parameter[] parameters = params(transactionStatusParam)
        return makeDelegatingClosureCall(transactionTemplateVar, executeMethodName, parameters, originalMethodCall, methodNode.getVariableScope())
    }

    protected String getTransactionTemplateMethodName() {
        return 'execute'
    }

    protected applyTransactionalAttributeSettings(AnnotationNode annotationNode, VariableExpression transactionAttributeVar, BlockStatement methodBody, ClassNode classNode, MethodNode methodNode) {
        // Set the transaction name
        String transactionName = "${classNode.name}.${methodNode.name}"
        methodBody.addStatement(
                assignS(propX(transactionAttributeVar, 'name'), new ConstantExpression(transactionName))
        )

        final ClassNode rollbackRuleAttributeClassNode = make(RollbackRuleAttribute)
        final ClassNode noRollbackRuleAttributeClassNode = make(NoRollbackRuleAttribute)
        final Map<String, Expression> members = annotationNode.getMembers()
        if (READ_ONLY_TYPE.equals(annotationNode.classNode)) {
            methodBody.addStatement(
                assignS(propX(transactionAttributeVar, 'readOnly'), ConstantExpression.TRUE)
            )
        }

        members.each { String name, Expression expr ->
            if (name == 'propagation') {
                Expression valExpr = expr
                if (expr instanceof PropertyExpression) {
                    valExpr = callX(expr, 'value')
                }
                methodBody.addStatement(
                    assignS(propX(transactionAttributeVar, 'propagationBehavior'), valExpr)
                )
            } else if (name == 'isolation') {
                Expression valExpr = expr
                if (expr instanceof PropertyExpression) {
                    valExpr = callX(expr, 'value')
                }
                methodBody.addStatement(
                    assignS(propX(transactionAttributeVar, 'isolationLevel'), valExpr)
                )
            } else if (name == 'timeout') {
                methodBody.addStatement(
                    assignS(propX(transactionAttributeVar, name), expr)
                )
            } else if (name == 'readOnly') {
                methodBody.addStatement(
                    assignS(propX(transactionAttributeVar, name), expr)
                )
            } else if (name == 'rollbackFor' || name == 'rollbackForClassName' || name == 'noRollbackFor' || name == 'noRollbackForClassName') {
                boolean isRollback = name.startsWith('rollbackFor')
                ClassNode ruleNode = isRollback ? rollbackRuleAttributeClassNode : noRollbackRuleAttributeClassNode
                String attributeName = 'rollbackRules'

                if (expr instanceof ListExpression) {
                    for (Expression e in ((ListExpression) expr).getExpressions()) {
                        methodBody.addStatement(
                            stmt(callX(propX(transactionAttributeVar, attributeName), 'add', ctorX(ruleNode, args(e))))
                        )
                    }
                } else {
                    methodBody.addStatement(
                        stmt(callX(propX(transactionAttributeVar, attributeName), 'add', ctorX(ruleNode, args(expr))))
                    )
                }
            } else if (name != 'connection' && name != 'value') {
                methodBody.addStatement(
                        assignS(propX(transactionAttributeVar, name), expr)
                )
            }
        }
    }

    @Override
    protected String getRenamedMethodPrefix() {
        return RENAMED_METHOD_PREFIX
    }

    @Override
    protected boolean hasLocalAnnotation(MethodNode amd, AnnotationNode classAnnotation) {
        return findAnnotation(amd, Transactional) != null || findAnnotation(amd, ReadOnly) != null || findAnnotation(amd, Rollback) != null
    }

    @Override
    int priority() {
        GroovyTransformOrder.TRANSACTIONAL_ORDER
    }
}
