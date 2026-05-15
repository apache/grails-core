/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.services.transform

import java.beans.Introspector
import java.lang.reflect.Modifier

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.FileReaderSource
import org.codehaus.groovy.control.io.ReaderSource
import org.codehaus.groovy.control.io.URLReaderSource
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.trait.TraitComposer

import groovyjarjarasm.asm.Opcodes

import org.springframework.transaction.PlatformTransactionManager

import grails.gorm.services.Service
import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.services.Implemented
import org.grails.datastore.gorm.services.ServiceEnhancer
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.ServiceImplementerAdapter
import org.grails.datastore.gorm.services.implementers.AdaptedImplementer
import org.grails.datastore.gorm.services.implementers.CountByImplementer
import org.grails.datastore.gorm.services.implementers.CountImplementer
import org.grails.datastore.gorm.services.implementers.CountWhereImplementer
import org.grails.datastore.gorm.services.implementers.DeleteImplementer
import org.grails.datastore.gorm.services.implementers.DeleteWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindAllByImplementer
import org.grails.datastore.gorm.services.implementers.FindAllByInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindAllImplementer
import org.grails.datastore.gorm.services.implementers.FindAllInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindAllPropertyProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindAllStringQueryImplementer
import org.grails.datastore.gorm.services.implementers.FindAllWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindAndDeleteImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindOneImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionStringQueryImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindOnePropertyProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindOneStringQueryImplementer
import org.grails.datastore.gorm.services.implementers.FindOneWhereImplementer
import org.grails.datastore.gorm.services.implementers.SaveImplementer
import org.grails.datastore.gorm.services.implementers.UpdateOneImplementer
import org.grails.datastore.gorm.services.implementers.UpdateStringQueryImplementer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.gorm.transform.AbstractTraitApplyingGormASTTransformation
import org.grails.datastore.gorm.validation.jakarta.services.implementers.MethodValidationImplementer
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.core.order.OrderedComparator
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_PARAMETERS

import static org.apache.groovy.ast.tools.AnnotatedNodeUtils.markAsGenerated
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.block
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifElseS
import static org.codehaus.groovy.ast.tools.GeneralUtils.notNullX
import static org.codehaus.groovy.ast.tools.GeneralUtils.param
import static org.codehaus.groovy.ast.tools.GeneralUtils.params
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.callD
import static org.grails.datastore.mapping.reflect.AstUtils.COMPILE_STATIC_TYPE
import static org.grails.datastore.mapping.reflect.AstAnnotationUtils.addAnnotationIfNecessary
import static org.grails.datastore.mapping.reflect.AstAnnotationUtils.findAnnotation
import static org.grails.datastore.mapping.reflect.AstUtils.copyAnnotations
import static org.grails.datastore.mapping.reflect.AstUtils.copyParameters
import static org.grails.datastore.mapping.reflect.AstUtils.error
import static org.grails.datastore.mapping.reflect.AstUtils.findAllUnimplementedAbstractMethods
import static org.grails.datastore.mapping.reflect.AstUtils.hasAnnotation
import static org.grails.datastore.mapping.reflect.AstUtils.warning

/**
 * Makes a class implement the {@link org.grails.datastore.mapping.services.Service} trait and generates the necessary
 * service loader META-INF/services file.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class ServiceTransformation extends AbstractTraitApplyingGormASTTransformation implements CompilationUnitAware, ASTTransformation {

    private static final ClassNode MY_TYPE = new ClassNode(Service)
    private static final Object APPLIED_MARKER = new Object()
    private static final List<ServiceImplementer> DEFAULT_IMPLEMENTORS = [
            new FindAllImplementer(),
            new FindOneImplementer(),
            new FindAllByImplementer(),
            new FindAllByInterfaceProjectionImplementer(),
            new FindOneByImplementer(),
            new FindOneByInterfaceProjectionImplementer(),
            new FindOneInterfaceProjectionImplementer(),
            new FindAllInterfaceProjectionImplementer(),
            new FindAndDeleteImplementer(),
            new DeleteImplementer(),
            new DeleteWhereImplementer(),
            new SaveImplementer(),
            new UpdateOneImplementer(),
            new FindOnePropertyProjectionImplementer(),
            new FindAllPropertyProjectionImplementer(),
            new FindOneWhereImplementer(),
            new FindOneInterfaceProjectionWhereImplementer(),
            new FindAllWhereImplementer(),
            new FindOneStringQueryImplementer(),
            new FindOneInterfaceProjectionStringQueryImplementer(),
            new FindAllStringQueryImplementer(),
            new UpdateStringQueryImplementer(),
            new CountImplementer(),
            new CountByImplementer(),
            new CountWhereImplementer(),
            new MethodValidationImplementer()] as List<ServiceImplementer>

    private static Iterable<ServiceImplementer> LOADED_IMPLEMENTORS = null
    public static
    final String NO_IMPLEMENTATIONS_MESSAGE = 'No implementations possible for method. Please use an abstract class instead and provide an implementation.'

    @Override
    protected Class getTraitClass() {

        org.grails.datastore.mapping.services.Service
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
    boolean shouldWeave(AnnotationNode annotationNode, ClassNode classNode) {
        return classNode.getNodeMetaData(APPLIED_MARKER) != APPLIED_MARKER
    }

    @Override
    void visitAfterTraitApplied(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        boolean isInterface = classNode.isInterface()
        boolean isAbstractClass = !isInterface && Modifier.isAbstract(classNode.modifiers)

        List<FieldNode> propertiesFields = []
        if (isAbstractClass || !isInterface) {
            List<PropertyNode> properties = classNode.getProperties().sort { it.name }
            for (PropertyNode pn in properties) {
                ClassNode propertyType = pn.type
                if (hasAnnotation(propertyType, Service) && propertyType != classNode && Modifier.isPublic(pn.modifiers) && pn.getterBlock == null && pn.setterBlock == null) {
                    FieldNode field = pn.field
                    propertiesFields.add(field)
                }
            }

            if (isAbstractClass) {
                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()
                if (!constructors.isEmpty()) {
                    error(sourceUnit, classNode, 'Abstract data Services should not define constructors')
                }
            }
        }

        propertiesFields.sort(true) { it.name } // ensure a consistent order of processing fields

        Expression valueMember = annotationNode.getMember('value')
        ClassExpression ce = valueMember instanceof ClassExpression ? (ClassExpression) valueMember : null
        ClassNode targetDomainClass = ce != null ? ce.type : ClassHelper.OBJECT_TYPE
        ClassNode datastoreType = ClassHelper.make(Datastore)

        if (isInterface || isAbstractClass) {
            // create a new class to represent the implementation
            String packageName = classNode.packageName ? "${classNode.packageName}." : ''
            ClassNode[] interfaces = isInterface ? ([classNode.plainNodeReference] as ClassNode[]) : new ClassNode[0]
            ClassNode superClass = isInterface ? ClassHelper.OBJECT_TYPE : classNode.plainNodeReference
            String serviceClassName = classNode.nameWithoutPackage
            ClassNode impl = new ClassNode("${packageName}\$${serviceClassName}Implementation", // class name
                    Opcodes.ACC_PUBLIC, // public
                    superClass,
                    interfaces)

            // weave the trait class
            weaveTraitWithGenerics(impl, getTraitClass(), targetDomainClass)

            addDatastoreMethods(impl, datastoreType, targetDomainClass, propertiesFields)

            copyAnnotations(classNode, impl)
            AnnotationNode serviceAnnotation = findAnnotation((AnnotatedNode)impl, Service)
            if (serviceAnnotation != null && serviceAnnotation.getMember('name') == null) {

                serviceAnnotation
                        .setMember('name', new ConstantExpression(Introspector.decapitalize(serviceClassName)))
            }
            // add compile static by default
            impl.addAnnotation(new AnnotationNode(COMPILE_STATIC_TYPE))

            // Auto-inherit datasource from domain class's mapping if the service
            // does not already have an explicit @Transactional(connection=...)
            if (targetDomainClass != ClassHelper.OBJECT_TYPE) {
                def domainConnection = resolveDomainDatasource(targetDomainClass)
                if (domainConnection != null
                        && ConnectionSource.DEFAULT != domainConnection
                        && ConnectionSource.ALL != domainConnection) {
                    if (!hasExplicitConnectionAnnotation(classNode)) {
                        applyDomainConnectionToService(classNode, impl, domainConnection)
                    }
                } else {
                    // Generate a default transaction manager getter for DEFAULT connections
                    generateDefaultTransactionManager(impl, targetDomainClass)
                }
            } else {
                generateDefaultTransactionManager(impl, targetDomainClass)
            }

            List<MethodNode> abstractMethods = findAllUnimplementedAbstractMethods(classNode)
            abstractMethods.sort(true) { it.name } // ensure a consistent order of processing methods

            Iterable<ServiceImplementer> implementers = findServiceImplementors(annotationNode)

            // first go through the existing implemented methods and just enhance them
            if (!isInterface) {
                def sortedMethods = classNode.methods.sort(true) { it.name }
                for (MethodNode existing in (sortedMethods)) {
                    int modifiers = existing.modifiers
                    if (!Modifier.isAbstract(modifiers) && Modifier.isPublic(modifiers) && !existing.isStatic()) {
                        for (ServiceImplementer implementer in implementers) {
                            if (implementer instanceof ServiceEnhancer) {
                                ServiceEnhancer enhancer = (ServiceEnhancer) implementer
                                if (enhancer.doesEnhance(targetDomainClass, existing)) {
                                    enhancer.enhance(targetDomainClass, existing, existing, impl)
                                }
                            }
                        }
                    }
                }
            }

            // go through the abstract methods and implement them
            for (MethodNode method in abstractMethods) {
                // find an implementer that implements the method
                MethodNode methodImpl = null
                for (ServiceImplementer implementer in implementers) {
                    if (implementer.doesImplement(targetDomainClass, method)) {
                        int modifiers = method.modifiers
                        if (Modifier.isAbstract(modifiers)) {
                            modifiers -= Modifier.ABSTRACT
                        }
                        if (isInterface) {
                            modifiers |= Modifier.PUBLIC
                        }
                        methodImpl = new MethodNode(
                                method.name,
                                modifiers,
                                GenericsUtils.makeClassSafeWithGenerics(method.returnType, method.returnType.genericsTypes),
                                copyParameters(method.parameters),
                                method.exceptions,
                                new BlockStatement())
                        methodImpl.setDeclaringClass(impl)
                        copyAnnotations(method, methodImpl)
                        markAsGenerated(impl, methodImpl)

                        if (Modifier.isProtected(method.modifiers)) {
                            if (!TransactionalTransform.hasTransactionalAnnotation(methodImpl) && findAnnotation((AnnotatedNode)methodImpl, NotTransactional) == null) {
                                addAnnotationIfNecessary((AnnotatedNode)methodImpl, NotTransactional)
                            }
                        }

                        implementer.implement(targetDomainClass, method, methodImpl, impl)

                        if (!Modifier.isProtected(method.modifiers)) {
                            if (!TransactionalTransform.hasTransactionalAnnotation(methodImpl)) {
                                addAnnotationIfNecessary((AnnotatedNode)methodImpl, ReadOnly)
                            }
                        }

                        def implementedAnn = new AnnotationNode(ClassHelper.make(Implemented))

                        Class implementedClass = implementer.getClass()
                        if (implementer instanceof AdaptedImplementer) {
                            implementedClass = ((AdaptedImplementer) implementer).getAdapted().getClass()
                        }
                        implementedAnn.setMember('by', classX(implementedClass))
                        methodImpl.addAnnotation(implementedAnn)
                        markAsGenerated(impl, methodImpl)
                        impl.addMethod(methodImpl)
                        break
                    }
                }

                // the method couldn't be implemented so error
                if (methodImpl == null) {
                    error(sourceUnit, classNode.isPrimaryClassNode() ? method : classNode, "No implementations possible for method '${method.typeDescriptor}'. Please use an abstract class instead and provide an implementation.")
                    break
                } else {

                    for (ServiceImplementer implementer in implementers) {
                        if (implementer instanceof ServiceEnhancer) {
                            ServiceEnhancer enhancer = ((ServiceEnhancer) implementer)
                            if (enhancer.doesEnhance(targetDomainClass, method)) {
                                enhancer.enhance(targetDomainClass, method, methodImpl, impl)
                            }
                        }
                    }
                }
            }

            if (compilationUnit != null) {
                TraitComposer.doExtendTraits(impl, sourceUnit, compilationUnit)
            }

            Expression exposeExpr = annotationNode.getMember('expose')
            if (exposeExpr == null || (exposeExpr instanceof ConstantExpression && exposeExpr == ConstantExpression.TRUE)) {
                generateServiceDescriptor(sourceUnit, impl)
            }

            sourceUnit.getAST().addClass(impl)
        } else {
            addDatastoreMethods(classNode, datastoreType, targetDomainClass, propertiesFields)
            Expression exposeExpr = annotationNode.getMember('expose')
            if (exposeExpr == null || (exposeExpr instanceof ConstantExpression && exposeExpr == ConstantExpression.TRUE)) {
                generateServiceDescriptor(sourceUnit, classNode)
            }
        }
    }

    private void addDatastoreMethods(ClassNode classNode, ClassNode datastoreType, ClassNode targetDomainClass, List<FieldNode> propertiesFields) {
        BlockStatement setterBody = block()
        Parameter datastoreParam = param(datastoreType, 'd')
        if (classNode.getDeclaredMethod('setDatastore', params(datastoreParam)) == null) {
            MethodNode datastoreSetterNode = classNode.addMethod('setDatastore', Modifier.PUBLIC, ClassHelper.VOID_TYPE, params(
                    datastoreParam
            ), null, setterBody)
            markAsGenerated(classNode, datastoreSetterNode)

            if (!propertiesFields.isEmpty()) {
                // If there are properties to inject, we use the setter to initialize them
                // but we don't want to store the datastore itself.
                VariableExpression datastoreVar = varX(datastoreParam)
                for (FieldNode fn in propertiesFields) {
                    setterBody.addStatement(
                            assignS(varX(fn), callX(datastoreVar, 'getService', classX(fn.type.plainNodeReference)))
                    )
                }
            }
        }

        if (classNode.getDeclaredMethod('getDatastore', ZERO_PARAMETERS) == null) {
            // Always override getDatastore() for dynamic resolution
            def apiResolverExpr = callX(callX(classX(GormRegistry), 'getInstance'), 'getApiResolver')
            MethodNode datastoreGetterNode = classNode.addMethod('getDatastore', Modifier.PUBLIC, datastoreType.plainNodeReference, ZERO_PARAMETERS, null,
                    returnS(callX(apiResolverExpr, 'findDatastore', args(classX(targetDomainClass))))
            )
            markAsGenerated(classNode, datastoreGetterNode)
        }
    }

    private Iterable<ServiceImplementer> addClassExpressionToImplementers(Expression exp, List implementers, Class type) {
        if (exp instanceof ClassExpression) {
            ClassNode cn = ((ClassExpression) exp).type
            if (!cn.isPrimaryClassNode()) {
                Class cls = cn.typeClass
                if (cls != null && type.isAssignableFrom(cls)) {
                    implementers.add(cls.newInstance())
                }
            }
        }
        return implementers
    }

    protected Iterable<ServiceImplementer> findServiceImplementors(AnnotationNode annotationNode) {
        if (LOADED_IMPLEMENTORS == null) {
            Iterable<ServiceImplementer> implementers = load(ServiceImplementer)
            implementers = (implementers + DEFAULT_IMPLEMENTORS).unique { ServiceImplementer o1 ->
                o1.class.name
            }

            List<ServiceImplementer> finalImplementers = []
            finalImplementers.addAll(implementers)

            loadAnnotationDefined(annotationNode, 'implementers', finalImplementers, ServiceImplementer)

            Iterable<ServiceImplementerAdapter> adapters = load(ServiceImplementerAdapter)
            List<ServiceImplementerAdapter> finalAdapters = adapters.toList().sort { it.class.name }
            loadAnnotationDefined(annotationNode, 'adapters', finalAdapters, ServiceImplementerAdapter)

            if (!finalAdapters.isEmpty()) {
                finalAdapters = finalAdapters.unique { ServiceImplementerAdapter o1 ->
                    o1.class.name
                }
                for (implementer in implementers) {
                    for (ServiceImplementerAdapter adapter in finalAdapters) {
                        ServiceImplementer adapted = adapter.adapt(implementer)
                        if (adapted != null) {
                            finalImplementers.add(adapted)
                        }
                    }
                }
            }

            LOADED_IMPLEMENTORS = finalImplementers.sort(true, new OrderedComparator<ServiceImplementer>())
        }
        return LOADED_IMPLEMENTORS
    }

    protected void loadAnnotationDefined(AnnotationNode annotationNode, String member, List finalList, Class type) {
        Expression additionalImplementers = annotationNode.getMember(member)
        if (additionalImplementers instanceof ListExpression) {
            for (Expression exp in ((ListExpression) additionalImplementers).expressions) {
                addClassExpressionToImplementers(exp, finalList, type)
            }
        } else if (additionalImplementers instanceof ClassExpression) {
            addClassExpressionToImplementers(additionalImplementers, finalList, type)
        }
    }

    protected <T> Iterable<T> load(Class<T> type) {
        Iterable<T> implementers = ServiceLoader.load(type, getClass().classLoader)
        if (!implementers.iterator().hasNext()) {
            implementers = ServiceLoader.load(type, Thread.currentThread().contextClassLoader)
        }
        return implementers
    }

    protected void generateServiceDescriptor(SourceUnit sourceUnit, ClassNode classNode) {
        ReaderSource readerSource = sourceUnit.getSource()
        // Don't generate for runtime compiled scripts
        if (readerSource instanceof FileReaderSource || readerSource instanceof URLReaderSource) {

            File targetDirectory = sourceUnit.configuration.targetDirectory
            if (targetDirectory == null) {
                targetDirectory = new File('build/resources/main')
            }

            File servicesDir = new File(targetDirectory, 'META-INF/services')
            servicesDir.mkdirs()

            String className = classNode.name
            try {
                def descriptor = new File(servicesDir, org.grails.datastore.mapping.services.Service.name)
                if (descriptor.exists()) {
                    String ls = System.getProperty('line.separator')
                    String contents = descriptor.text
                    def entries = contents.split('\\n')
                    if (!entries.contains(className)) {
                        descriptor.append("${ls}${className}")
                    }
                } else {
                    descriptor.text = className
                }
            } catch (Throwable e) {
                warning(sourceUnit, classNode, "Error generating service loader descriptor for class [${className}]: $e.message")
            }
        }
    }

    private static String resolveDomainDatasource(ClassNode domainClass) {
        def mappingField = domainClass.getDeclaredField('mapping')
        if (mappingField == null) {
            def mappingProp = domainClass.getProperty('mapping')
            if (mappingProp != null) {
                mappingField = mappingProp.field
            }
        }
        if (mappingField != null) {
            return extractDatasourceFromExpression(mappingField.initialValueExpression)
        }
        return null
    }

    private static String extractDatasourceFromExpression(Expression expr) {
        if (!(expr instanceof ClosureExpression)) {
            return null
        }
        def code = ((ClosureExpression) expr).getCode()
        if (!(code instanceof BlockStatement)) {
            return null
        }
        for (def stmt : ((BlockStatement) code).statements) {
            if (!(stmt instanceof ExpressionStatement)) {
                continue
            }
            def stmtExpr = ((ExpressionStatement) stmt).expression
            if (!(stmtExpr instanceof MethodCallExpression)) {
                continue
            }
            def call = (MethodCallExpression) stmtExpr
            def methodName = call.methodAsString
            if ('datasource' == methodName || 'connection' == methodName || 'connections' == methodName) {
                def args = call.arguments
                if (args instanceof ArgumentListExpression) {
                    def argExprs = ((ArgumentListExpression) args).expressions
                    if (!argExprs.isEmpty() && argExprs[0] instanceof ConstantExpression) {
                        return ((ConstantExpression) argExprs[0]).value?.toString()
                    }
                }
            }
        }
        return null
    }

    private static boolean hasExplicitConnectionAnnotation(ClassNode classNode) {
        AnnotationNode ann = findAnnotation((AnnotatedNode)classNode, Transactional)
        if (ann != null) {
            Expression connection = ann.getMember('connection')
            if (connection instanceof ConstantExpression) {
                def value = ((ConstantExpression) connection).value?.toString()
                return value != null && !value.isEmpty()
            }
        }
        return false
    }

    private static void applyDomainConnectionToService(ClassNode classNode, ClassNode implClass, String connectionName) {
        def connectionExpr = new ConstantExpression(connectionName)
        applyDomainConnection(classNode, connectionExpr)
        applyDomainConnection(implClass, connectionExpr)

        // TransactionalTransform runs before ServiceTransformation creates the impl class, so it never
        // gets a chance to weave getTransactionManager() with the correct connection-aware logic.
        // We generate it here directly to ensure the right transaction manager is used at runtime.
        generateConnectionAwareTransactionManager(implClass, connectionExpr)
    }

    private static void applyDomainConnection(ClassNode node, ConstantExpression connectionExpr) {
        AnnotationNode ann = findAnnotation((AnnotatedNode)node, Transactional)
        if (ann != null) {
            ann.setMember('connection', connectionExpr)
        }
        else {
            AnnotationNode newAnn = new AnnotationNode(ClassHelper.make(Transactional))
            newAnn.setMember('connection', connectionExpr)
            node.addAnnotation(newAnn)
        }
    }

    /**
     * Generates a {@code getTransactionManager()} method on the impl class that resolves the
     * transaction manager for the inherited connection source. This mirrors the logic in
     * {@link org.grails.datastore.gorm.transactions.transform.TransactionalTransform#weaveTransactionManagerAware}
     * for classes implementing the {@link org.grails.datastore.mapping.services.Service} trait.
     */
    private static void generateConnectionAwareTransactionManager(ClassNode implClass, ConstantExpression connectionExpr) {
        // Remove any existing getTransactionManager() that was added without connection awareness
        implClass.getMethods('getTransactionManager').each {
            implClass.removeMethod(it)
        }

        def transactionManagerClassNode = ClassHelper.make(PlatformTransactionManager)
        def transactionCapableDatastore = ClassHelper.make(TransactionCapableDatastore)
        def multipleConnectionDatastore = ClassHelper.make(MultipleConnectionSourceCapableDatastore)
        def registryExpr = callX(classX(GormRegistry), 'getInstance')

        // getDatastore() call (method from Service trait or overridden on impl)
        def datastoreVar = callX(varX('this'), 'getDatastore')
        // ((MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(connectionName)
        def datastoreForConnection = callD(
                castX(multipleConnectionDatastore, datastoreVar),
                'getDatastoreForConnection',
                connectionExpr
        )
        // .getTransactionManager()
        def datastoreTxManager = propX(
                castX(transactionCapableDatastore, datastoreForConnection),
                'transactionManager'
        )
        // GormRegistry.getInstance().findSingleTransactionManager(connectionName)
        def fallbackTxManager = callX(
                registryExpr,
                'findSingleTransactionManager',
                args(connectionExpr)
        )

        // if (datastore != null) { return <datastoreTxManager> } else { return <fallbackTxManager> }
        def body = ifElseS(
                notNullX(datastoreVar),
                returnS(datastoreTxManager),
                returnS(fallbackTxManager)
        )

        def methodNode = implClass.addMethod(
                'getTransactionManager',
                Modifier.PUBLIC,
                transactionManagerClassNode,
                ZERO_PARAMETERS, null,
                body
        )
        markAsGenerated(implClass, methodNode)
    }

    private static void generateDefaultTransactionManager(ClassNode implClass, ClassNode targetDomainClass) {
        // Remove any existing getTransactionManager() that was added without connection awareness
        implClass.getMethods('getTransactionManager').each {
            implClass.removeMethod(it)
        }

        def transactionManagerClassNode = ClassHelper.make(PlatformTransactionManager)
        def transactionCapableDatastore = ClassHelper.make(TransactionCapableDatastore)
        def registryExpr = callX(classX(GormRegistry), 'getInstance')

        // getDatastore() call (method from Service trait or overridden on impl)
        def datastoreVar = callX(varX('this'), 'getDatastore')
        // .getTransactionManager()
        def datastoreTxManager = propX(
                castX(transactionCapableDatastore, datastoreVar),
                'transactionManager'
        )
        // GormRegistry.getInstance().findSingleTransactionManager()
        def fallbackTxManager = callX(
                registryExpr,
                'findSingleTransactionManager'
        )

        // if (datastore != null) { return <datastoreTxManager> } else { return <fallbackTxManager> }
        def body = ifElseS(
                notNullX(datastoreVar),
                returnS(datastoreTxManager),
                returnS(fallbackTxManager)
        )

        def methodNode = implClass.addMethod(
                'getTransactionManager',
                Modifier.PUBLIC,
                transactionManagerClassNode,
                ZERO_PARAMETERS, null,
                body
        )
        markAsGenerated(implClass, methodNode)
    }

    @Override
    int priority() {
        GroovyTransformOrder.DATA_SERVICE_ORDER
    }
}
