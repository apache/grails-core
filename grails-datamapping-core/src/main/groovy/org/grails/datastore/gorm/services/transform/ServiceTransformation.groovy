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
package org.grails.datastore.gorm.services.transform

import grails.gorm.services.Service
import grails.gorm.transactions.NotTransactional
import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.FileReaderSource
import org.codehaus.groovy.control.io.ReaderSource
import org.codehaus.groovy.control.io.URLReaderSource
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.trait.TraitComposer
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
import org.grails.datastore.gorm.services.implementers.FindAllByInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindAllInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindAllStringQueryImplementer
import org.grails.datastore.gorm.services.implementers.FindAllWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindAndDeleteImplementer
import org.grails.datastore.gorm.services.implementers.FindAllImplementer
import org.grails.datastore.gorm.services.implementers.FindAllByImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindOneImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionStringQueryImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindOnePropertyProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindAllPropertyProjectionImplementer
import org.grails.datastore.gorm.services.implementers.SaveImplementer
import org.grails.datastore.gorm.services.implementers.FindOneStringQueryImplementer
import org.grails.datastore.gorm.services.implementers.UpdateOneImplementer
import org.grails.datastore.gorm.services.implementers.FindOneWhereImplementer
import org.grails.datastore.gorm.services.implementers.UpdateStringQueryImplementer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.gorm.transform.AbstractTraitApplyingGormASTTransformation
import org.grails.datastore.gorm.validation.jakarta.services.implementers.MethodValidationImplementer
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.order.OrderedComparator

import java.beans.Introspector
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.grails.datastore.mapping.reflect.AstUtils.*

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

    private static final ClassNode MY_TYPE = new ClassNode(Service.class);
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
    final String NO_IMPLEMENTATIONS_MESSAGE = "No implementations possible for method. Please use an abstract class instead and provide an implementation."

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
        return !Modifier.isAbstract(classNode.modifiers)
    }

    @Override
    void visitAfterTraitApplied(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        // if the class node is an interface we are going to try and generate an implementation
        // and add the implementation as an inner class. If any method of the interface cannot be implemented
        // a compilation error occurs
        boolean isInterface = classNode.isInterface()
        boolean isAbstractClass = !isInterface && Modifier.isAbstract(classNode.modifiers)

        List<FieldNode> propertiesFields = []
        if (isAbstractClass) {
            List<PropertyNode> properties = classNode.getProperties()
            for (PropertyNode pn in properties) {
                ClassNode propertyType = pn.type
                if (hasAnnotation(propertyType, Service) && propertyType != classNode && Modifier.isPublic(pn.modifiers) && pn.getterBlock == null && pn.setterBlock == null) {
                    FieldNode field = pn.field
                    VariableExpression fieldVar = varX(field)
                    propertiesFields.add(field)
                    pn.setGetterBlock(
                        block(
                            ifS( equalsNullX(fieldVar),
                                assignX(fieldVar, callX( varX("datastore"), "getService", classX(propertyType.plainNodeReference)))
                            ),
                            returnS(fieldVar)
                        )
                    )
                }
            }

            List<ConstructorNode> constructors = classNode.getDeclaredConstructors()
            if(!constructors.isEmpty()) {
                error(sourceUnit, classNode, "Abstract data Services should not define constructors")
            }

        }

        if (isInterface || isAbstractClass) {
            // create a new class to represent the implementation
            String packageName = classNode.packageName ? "${classNode.packageName}." : ""
            ClassNode[] interfaces = isInterface ? ([classNode.plainNodeReference] as ClassNode[]) : new ClassNode[0]
            ClassNode superClass = isInterface ? ClassHelper.OBJECT_TYPE : classNode.plainNodeReference
            String serviceClassName = classNode.nameWithoutPackage
            ClassNode impl = new ClassNode("${packageName}\$${serviceClassName}Implementation", // class name
                    Opcodes.ACC_PUBLIC, // public
                    superClass,
                    interfaces)

            if(!propertiesFields.isEmpty()) {

                ClassNode datastoreType = ClassHelper.make(Datastore)
                FieldNode datastoreField = impl.addField("datastore", Modifier.PRIVATE, datastoreType, null)
                VariableExpression datastoreFieldVar = varX(datastoreField)


                BlockStatement body = block()
                Parameter datastoreParam = param(datastoreType, "d")
                impl.addMethod("setDatastore", Modifier.PUBLIC, ClassHelper.VOID_TYPE, params(
                        datastoreParam
                ), null, body )
                body.addStatement(
                        assignS(datastoreFieldVar, varX(datastoreParam))
                )
                impl.addMethod("getDatastore", Modifier.PUBLIC, datastoreType.plainNodeReference, ZERO_PARAMETERS, null,
                        returnS( datastoreFieldVar )
                )
                for(FieldNode fn in propertiesFields) {
                    body.addStatement(
                            assignS(varX(fn), callX(datastoreFieldVar, "getService", classX(fn.type.plainNodeReference)))
                    )
                }
            }

            copyAnnotations(classNode, impl)
            AnnotationNode serviceAnnotation = findAnnotation(impl, Service)
            if(serviceAnnotation.getMember("name") == null) {
                serviceAnnotation
                        .setMember("name", new ConstantExpression(Introspector.decapitalize(serviceClassName)))
            }
            // add compile static by default
            impl.addAnnotation(new AnnotationNode(COMPILE_STATIC_TYPE))
            // weave the trait class
            ClassExpression ce = (ClassExpression) annotationNode.getMember("value")
            ClassNode targetDomainClass = ce != null ? ce.type : ClassHelper.OBJECT_TYPE
            // weave with generic argument
            weaveTraitWithGenerics(impl, getTraitClass(), targetDomainClass)

            List<MethodNode> abstractMethods = findAllUnimplementedAbstractMethods(classNode)
            Iterable<ServiceImplementer> implementers = findServiceImplementors(annotationNode)


            // first go through the existing implemented methods and just enhance them
            if (!isInterface) {
                for (MethodNode existing in classNode.methods) {
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
                        methodImpl = new MethodNode(
                                method.name,
                                Modifier.PUBLIC,
                                GenericsUtils.makeClassSafeWithGenerics(method.returnType, method.returnType.genericsTypes),
                                copyParameters(method.parameters),
                                method.exceptions,
                                new BlockStatement())
                        methodImpl.setDeclaringClass(impl)
                        if (Modifier.isProtected(method.modifiers)) {
                            if (!TransactionalTransform.hasTransactionalAnnotation(methodImpl)) {
                                addAnnotationIfNecessary(methodImpl, NotTransactional)
                            }
                        }
                        implementer.implement(targetDomainClass, method, methodImpl, impl)
                        def implementedAnn = new AnnotationNode(ClassHelper.make(Implemented))
                        Class implementedClass = implementer.getClass()
                        if(implementer instanceof AdaptedImplementer) {
                            implementedClass = ((AdaptedImplementer)implementer).getAdapted().getClass()
                        }
                        implementedAnn.setMember("by", classX(implementedClass))
                        methodImpl.addAnnotation(implementedAnn)
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


            Expression exposeExpr = annotationNode.getMember("expose")
            if (exposeExpr == null || (exposeExpr instanceof ConstantExpression && exposeExpr == ConstantExpression.TRUE)) {
                generateServiceDescriptor(sourceUnit, impl)
            }

            sourceUnit.getAST().addClass(impl)
        } else {
            Expression exposeExpr = annotationNode.getMember("expose")
            if (exposeExpr == null || (exposeExpr instanceof ConstantExpression && exposeExpr == ConstantExpression.TRUE)) {
                generateServiceDescriptor(sourceUnit, classNode)
            }
        }
    }

    private Iterable<ServiceImplementer> addClassExpressionToImplementers(Expression exp, List implementers, Class type) {
        if (exp instanceof ClassExpression) {
            ClassNode cn = ((ClassExpression) exp).type
            if (!cn.isPrimaryClassNode()) {
                Class cls = cn.typeClass
                if (cls != null && type.isAssignableFrom(cls)) {
                    implementers.add( cls.newInstance() )
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

            loadAnnotationDefined(annotationNode, "implementers", finalImplementers, ServiceImplementer)

            Iterable<ServiceImplementerAdapter> adapters = load(ServiceImplementerAdapter)
            List<ServiceImplementerAdapter> finalAdapters = adapters.toList()
            loadAnnotationDefined(annotationNode, "adapters", finalAdapters, ServiceImplementerAdapter)

            if(!finalAdapters.isEmpty()) {
                finalAdapters = finalAdapters.unique { ServiceImplementerAdapter o1 ->
                    o1.class.name
                }
                for(implementer in implementers) {
                    for(ServiceImplementerAdapter adapter in finalAdapters) {
                        ServiceImplementer adapted = adapter.adapt(implementer)
                        if(adapted != null) {
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
        Expression additionalImplementers = annotationNode.getMember(member )
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
                targetDirectory = new File("build/resources/main")
            }

            File servicesDir = new File(targetDirectory, "META-INF/services")
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
}
