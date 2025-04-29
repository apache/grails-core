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
package org.grails.compiler.injection;

import grails.artefact.Artefact;
import grails.compiler.ast.AnnotatedClassInjector;
import grails.compiler.ast.GrailsArtefactClassInjector;
import org.apache.groovy.ast.tools.AnnotatedNodeUtils;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Abstract transformer that takes an implementation class and creates methods
 * in a target ClassNode that delegate to that implementation class. Subclasses
 * should override to provide the implementation class details
 *
 * @since 2.0
 * @author  Graeme Rocher
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractGrailsArtefactTransformer implements GrailsArtefactClassInjector, AnnotatedClassInjector, Comparable {


    private static final Set<String> KNOWN_TRANSFORMED_CLASSES = new LinkedHashSet<String>();
    private static final String INSTANCE_PREFIX = "instance";
    private static final String STATIC_PREFIX = "static";
    private static final AnnotationNode AUTO_WIRED_ANNOTATION = new AnnotationNode(new ClassNode(Autowired.class));

    protected static final ClassNode OBJECT_CLASS = new ClassNode(Object.class);

    public static final int PUBLIC_STATIC_MODIFIER = Modifier.PUBLIC | Modifier.STATIC;
    public static final String CURRENT_PREFIX = "current";
    public static final String METHOD_MISSING_METHOD_NAME = "methodMissing";
    public static final String STATIC_METHOD_MISSING_METHOD_NAME = "$static_methodMissing";

    private static final String[] DEFAULT_GENERICS_PLACEHOLDERS = new String[]{"D", "T"};

    private final Set<String> classesTransformedByThis = new LinkedHashSet<String>();

    public String[] getArtefactTypes() {
        return new String[]{ getArtefactType() };
    }

    protected String getArtefactType() {
        String name = getClass().getSimpleName();
        if(name.endsWith("Transformer")) {
            return name.substring(0, name.length() - 11);
        }
        return name;
    }

    public void clearCachedState() {
        classesTransformedByThis.clear();
    }

    /**
     * Used for ordering not equality.
     *
     * Note: this class has a natural ordering that is inconsistent with equals.
     *
     * @see Comparable#compareTo(Object)
     */
    public int compareTo(Object o) {
        return 0; // treat all as the same by default for ordering
    }

    public void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if(shouldSkipInjection(classNode) || hasArtefactAnnotation(classNode)) return;
        performInjectionOnAnnotatedClass(source, context, classNode);
    }

    @Override
    public void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, null, classNode);
    }

    public void performInjectionOnAnnotatedClass(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if(shouldSkipInjection(classNode)) return;

        final String className = classNode.getName();
        KNOWN_TRANSFORMED_CLASSES.add(className);
        classesTransformedByThis.add(className);

        Map<String, ClassNode> genericsPlaceholders = resolveGenericsPlaceHolders(classNode);

        Class instanceImplementation = getInstanceImplementation();
        if (instanceImplementation != null) {
            performInstanceImplementationInjection(source, classNode, genericsPlaceholders, instanceImplementation);
        }

        Class staticImplementation = getStaticImplementation();
        if (staticImplementation != null) {
            performStaticImplementationInjection(classNode, genericsPlaceholders, staticImplementation);
        }

        addEnhancedAnnotation(classNode);
    }

    protected void performInstanceImplementationInjection(SourceUnit source, ClassNode classNode,
            Map<String, ClassNode> genericsPlaceholders, Class instanceImplementation) {
        ClassNode implementationNode;
        final ConstructorCallExpression constructorCallExpression;
        try {
            implementationNode = GrailsASTUtils.replaceGenericsPlaceholders(ClassHelper.make(instanceImplementation), genericsPlaceholders);
            constructorCallExpression = GrailsASTUtils.hasZeroArgsConstructor(implementationNode) ? new ConstructorCallExpression(implementationNode, ZERO_ARGS) : null;
        } catch (Throwable e) {
            // if we get here it means we have reached a point where there were errors loading the class to perform injection with, probably due to missing dependencies
            // this may well be ok, as we want to be able to compile against, for example, non servlet environments. In this case just bail out.
            return;
        }

        String apiInstanceProperty = INSTANCE_PREFIX + instanceImplementation.getSimpleName();
        Expression apiInstance = new VariableExpression(apiInstanceProperty, implementationNode);

        if (requiresStaticLookupMethod()) {
            final String lookupMethodName = CURRENT_PREFIX + instanceImplementation.getSimpleName();
            MethodNode lookupMethod = createStaticLookupMethod(classNode, implementationNode, apiInstanceProperty, lookupMethodName);
            apiInstance = new MethodCallExpression(new ClassExpression(classNode), lookupMethodName, ZERO_ARGS);
            ((MethodCallExpression)apiInstance).setMethodTarget(lookupMethod);
        }
        else if (requiresAutowiring()) {
            PropertyNode propertyNode = new PropertyNode(apiInstanceProperty, Modifier.PUBLIC, implementationNode, classNode, constructorCallExpression, null, null);
            propertyNode.addAnnotation(AUTO_WIRED_ANNOTATION);
            if(getMarkerAnnotation() != null) {
                propertyNode.addAnnotation(getMarkerAnnotation());
            }
            classNode.addProperty(propertyNode);
        }
        else {
            FieldNode fieldNode = classNode.getField(apiInstanceProperty);
            if (fieldNode == null || (Modifier.isPrivate(fieldNode.getModifiers()) && !fieldNode.getDeclaringClass().equals(classNode))) {
                fieldNode = new FieldNode(apiInstanceProperty, PRIVATE_STATIC_MODIFIER,implementationNode, classNode,constructorCallExpression);
                classNode.addField(fieldNode);
            }
        }

        while (!implementationNode.equals(AbstractGrailsArtefactTransformer.OBJECT_CLASS)) {
            List<MethodNode> declaredMethods = implementationNode.getMethods();
            for (MethodNode declaredMethod : declaredMethods) {
                if (GrailsASTUtils.isConstructorMethod(declaredMethod)) {
                    GrailsASTUtils.addDelegateConstructor(classNode, declaredMethod, genericsPlaceholders);
                }
                else if (isCandidateInstanceMethod(classNode, declaredMethod)) {
                    addDelegateInstanceMethod(classNode, apiInstance, declaredMethod, getMarkerAnnotation(), genericsPlaceholders);
                }
            }
            implementationNode = implementationNode.getSuperClass();
        }
        performInjectionInternal(apiInstanceProperty, source, classNode);
    }

    protected void performStaticImplementationInjection(ClassNode classNode,
            Map<String, ClassNode> genericsPlaceholders, Class staticImplementation) {
        ClassNode staticImplementationNode = GrailsASTUtils.replaceGenericsPlaceholders(ClassHelper.make(staticImplementation), genericsPlaceholders);

        final List<MethodNode> declaredMethods = staticImplementationNode.getMethods();
        final String staticImplementationSimpleName = staticImplementation.getSimpleName();
        String apiInstanceProperty = STATIC_PREFIX + staticImplementationSimpleName;
        final String lookupMethodName = CURRENT_PREFIX + staticImplementationSimpleName;

        if (!requiresStaticLookupMethod()) {
            final ConstructorCallExpression constructorCallExpression = new ConstructorCallExpression(staticImplementationNode, ZERO_ARGS);
            addApiLookupFieldAndSetter(classNode, staticImplementationNode, apiInstanceProperty, constructorCallExpression);
        }

        MethodNode lookupMethod = createStaticLookupMethod(classNode, staticImplementationNode, apiInstanceProperty, lookupMethodName);
        MethodCallExpression apiLookupMethod = new MethodCallExpression(new ClassExpression(classNode), lookupMethodName, ZERO_ARGS);
        apiLookupMethod.setMethodTarget(lookupMethod);        

        for (MethodNode declaredMethod : declaredMethods) {
            if (isStaticCandidateMethod(classNode,declaredMethod)) {
                addDelegateStaticMethod(classNode, apiLookupMethod, declaredMethod, genericsPlaceholders);
            }
        }
    }

    protected void addEnhancedAnnotation(ClassNode classNode) {
        GrailsASTUtils.addEnhancedAnnotation(classNode);
    }

    protected boolean shouldSkipInjection(ClassNode classNode) {
        return !isValidTargetClassNode(classNode)
                || (!isValidArtefactType() && !isValidArtefactTypeByConvention(classNode)) || classesTransformedByThis.contains(classNode.getName());
    }

    protected boolean hasArtefactAnnotation(ClassNode classNode) {
        return !classNode.getAnnotations(new ClassNode(Artefact.class)).isEmpty();
    }

    protected boolean isValidTargetClassNode(ClassNode classNode) {
        if(classNode.isEnum()) return false; // don't transform enums
        if(classNode instanceof InnerClassNode) return false;
        if(classNode.getName().contains("$")) return false;
        return true;
    }

    protected boolean isValidArtefactType() {
        return DomainClassArtefactHandler.TYPE.equals(getArtefactType());
    }

    protected Map<String, ClassNode> resolveGenericsPlaceHolders(ClassNode classNode) {
        Map<String, ClassNode> genericsPlaceHolders = new LinkedHashMap<String, ClassNode>();
        for(String placeHolder : DEFAULT_GENERICS_PLACEHOLDERS) {
            genericsPlaceHolders.put(placeHolder, classNode);
        }
        return genericsPlaceHolders;
    }

    protected void addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod, AnnotationNode markerAnnotation, Map<String, ClassNode> genericsPlaceholders) {
        GrailsASTUtils.addCompileStaticAnnotation(GrailsASTUtils.addDelegateInstanceMethod(classNode, delegate, declaredMethod, getMarkerAnnotation(), true, genericsPlaceholders, true));
    }

    protected void addDelegateStaticMethod(ClassNode classNode, MethodCallExpression apiLookupMethod,
            MethodNode declaredMethod, Map<String, ClassNode> genericsPlaceholders) {
        GrailsASTUtils.addCompileStaticAnnotation(GrailsASTUtils.addDelegateStaticMethod(apiLookupMethod, classNode, declaredMethod, getMarkerAnnotation(), genericsPlaceholders, true));
    }

    private boolean isValidArtefactTypeByConvention(ClassNode classNode) {
        String[] artefactTypes = getArtefactTypes();
        for (String artefactType : artefactTypes) {
            if(artefactType.equals("*")) return true;
            if(classNode.getName().endsWith(artefactType)) return true;
        }
        return false;
    }

    protected boolean isCandidateInstanceMethod(ClassNode classNode, MethodNode declaredMethod) {
        return GrailsASTUtils.isCandidateInstanceMethod(classNode, declaredMethod);
    }

    protected boolean isStaticCandidateMethod(ClassNode classNode, MethodNode declaredMethod) {
        return isStaticMethodIncluded(classNode, declaredMethod) || (!isStaticMethodExcluded(classNode, declaredMethod) && GrailsASTUtils.isCandidateMethod(declaredMethod));
    }

    protected boolean isStaticMethodExcluded(ClassNode classNode, MethodNode declaredMethod) {
        return GrailsASTUtils.isSetterOrGetterMethod(declaredMethod);
    }
    
    protected boolean isStaticMethodIncluded(ClassNode classNode, MethodNode declaredMethod) {
        return false;
    }

    private MethodNode createStaticLookupMethod(ClassNode classNode, ClassNode implementationNode, String apiProperty, String lookupMethodName) {
        // if autowiring is required we add a default method that throws an exception
        // the method should be override via meta-programming in the Grails environment
        MethodNode lookupMethod = classNode.getMethod(lookupMethodName, ZERO_PARAMETERS);
        if (lookupMethod == null  || !lookupMethod.getDeclaringClass().equals(classNode)) {
            BlockStatement methodBody = new BlockStatement();
            lookupMethod = populateAutowiredApiLookupMethod(classNode, implementationNode, apiProperty, lookupMethodName, methodBody);
            classNode.addMethod(lookupMethod);
            GrailsASTUtils.addCompileStaticAnnotation(lookupMethod);
            AnnotatedNodeUtils.markAsGenerated(classNode, lookupMethod);
        }
        return lookupMethod;
    }

    /**
     * Subclasses should override in the instance API requires a static lookup method instead of autowiring.
     * Defaults to false.
     *
     * @return Whether a static lookup method is used for the instance API
     */
    protected boolean requiresStaticLookupMethod() {
        return false;
    }

    protected MethodNode populateAutowiredApiLookupMethod(ClassNode classNode, ClassNode implementationNode,
                                                          String apiProperty, String methodName, BlockStatement methodBody) {
        
        addApiLookupFieldAndSetter(classNode, implementationNode, apiProperty, null);
        
        VariableExpression apiVar = new VariableExpression(apiProperty, implementationNode);
        
        BlockStatement ifBlock = new BlockStatement();
        ArgumentListExpression arguments = new ArgumentListExpression();
        arguments.addExpression(new ConstantExpression("Method on class ["+classNode+"] was used outside of a Grails application. If running in the context of a test using the mocking API or bootstrap Grails correctly."));
        ifBlock.addStatement(new ThrowStatement(new ConstructorCallExpression(new ClassNode(IllegalStateException.class), arguments)));        
        BlockStatement elseBlock = new BlockStatement();
        elseBlock.addStatement(new ReturnStatement(apiVar));
        methodBody.addStatement(new IfStatement(new BooleanExpression(new BinaryExpression(apiVar, GrailsASTUtils.EQUALS_OPERATOR, GrailsASTUtils.NULL_EXPRESSION)),ifBlock,elseBlock));
        
        MethodNode methodNode = new MethodNode(methodName, PUBLIC_STATIC_MODIFIER, implementationNode,ZERO_PARAMETERS,null,methodBody);        
        return methodNode;
    }

    protected void addApiLookupFieldAndSetter(ClassNode classNode, ClassNode implementationNode,
            String apiProperty, Expression initialValueExpression) {
        FieldNode fieldNode = classNode.getField(apiProperty);
        if (fieldNode == null || !fieldNode.getDeclaringClass().equals(classNode)) {
            fieldNode = new FieldNode(apiProperty, Modifier.PRIVATE | Modifier.STATIC, implementationNode, classNode, initialValueExpression);
            classNode.addField(fieldNode);
            
            String setterName = "set" + MetaClassHelper.capitalize(apiProperty);
            Parameter setterParameter = new Parameter(implementationNode, apiProperty);
            BlockStatement setterBody = new BlockStatement();
            setterBody.addStatement(new ExpressionStatement(new BinaryExpression(new AttributeExpression(
                    new ClassExpression(classNode), new ConstantExpression(apiProperty)), Token.newSymbol(Types.EQUAL, 0, 0),
                    new VariableExpression(setterParameter))));

            MethodNode methodNode = classNode.addMethod(setterName, Modifier.PUBLIC | Modifier.STATIC, ClassHelper.VOID_TYPE, new Parameter[]{setterParameter}, null, setterBody);
            GrailsASTUtils.addCompileStaticAnnotation(methodNode);
            AnnotatedNodeUtils.markAsGenerated(classNode, methodNode);
        }
    }

    protected MethodNode populateDefaultApiLookupMethod(ClassNode implementationNode, String apiInstanceProperty, String methodName, BlockStatement methodBody) {
        methodBody.addStatement(new ReturnStatement(new VariableExpression(apiInstanceProperty, implementationNode)));
        return new MethodNode(methodName, Modifier.PRIVATE, implementationNode,ZERO_PARAMETERS,null,methodBody);
    }

    /**
     * If the API requires autowiring then a @Autowired property will be added. If not a private field
     * that instantiates the API will be crated. Defaults to true.
     *
     * @return Whether autowiring is required
     */
    protected boolean requiresAutowiring() {
        return true;
    }

    /**
     * Subclasses can override to provide additional transformation
     *
     * @param apiInstanceProperty
     * @param source The source
     * @param classNode The class node
     */
    protected void performInjectionInternal(String apiInstanceProperty, SourceUnit source, ClassNode classNode) {
        // do nothing
    }

    /**
     * The class that provides the implementation of all instance methods and properties
     *
     * @return A class whose methods contain a first argument of type object that is the instance
     */
    public abstract Class getInstanceImplementation();

    /**
     * The class that provides static methods
     *
     * @return A class or null if non is provided
     */
    public abstract Class getStaticImplementation();

    public void performInjection(SourceUnit source, ClassNode classNode) {
        performInjection(source, null, classNode);
    }

    /**
     * A marker annotation to be applied to added methods, defaults to null
     *
     * @return The annotation node
     */
    protected AnnotationNode getMarkerAnnotation() {
        return null;
    }

    public static Collection<String> getTransformedClassNames() {
        return Collections.unmodifiableCollection( KNOWN_TRANSFORMED_CLASSES );
    }

    public static void addToTransformedClasses(String name) {
        KNOWN_TRANSFORMED_CLASSES.add(name);
    }
}
