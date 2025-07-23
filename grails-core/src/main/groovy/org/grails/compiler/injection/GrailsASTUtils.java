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

import grails.artefact.Enhanced;
import grails.compiler.ast.GrailsArtefactClassInjector;
import grails.util.GrailsNameUtils;
import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import groovy.transform.CompileStatic;
import groovy.transform.TypeChecked;
import groovy.transform.TypeCheckingMode;
import org.apache.groovy.ast.tools.AnnotatedNodeUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.sc.StaticCompileTransformation;
import org.codehaus.groovy.transform.trait.Traits;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.io.support.FileSystemResource;
import org.grails.io.support.Resource;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper methods for working with Groovy AST trees.
 *
 * @author Graeme Rocher
 * @since 0.3
 */
public class GrailsASTUtils {

    public static final String DOMAIN_DIR = "domain";
    public static final String GRAILS_APP_DIR = "grails-app";
    public static final String METHOD_MISSING_METHOD_NAME = "methodMissing";
    public static final String STATIC_METHOD_MISSING_METHOD_NAME = "$static_methodMissing";
    public static final Token EQUALS_OPERATOR = Token.newSymbol("==", 0, 0);
    public static final Token LOGICAL_AND_OPERATOR = Token.newSymbol("&&", 0, 0);
    public static final Token NOT_EQUALS_OPERATOR = Token.newSymbol("!=", 0, 0);
    public static final String OBJECT_CLASS = "java.lang.Object";

    private static final ClassNode ENHANCED_CLASS_NODE = new ClassNode(Enhanced.class);
    public static final ClassNode MISSING_METHOD_EXCEPTION = new ClassNode(MissingMethodException.class);
    public static final ConstantExpression NULL_EXPRESSION = new ConstantExpression(null);
    public static final Token ASSIGNMENT_OPERATOR = Token.newSymbol(Types.ASSIGNMENT_OPERATOR, 0, 0);
    public static final ClassNode OBJECT_CLASS_NODE = new ClassNode(Object.class).getPlainNodeReference();
    public static final ClassNode VOID_CLASS_NODE = ClassHelper.VOID_TYPE;
    public static final ClassNode INTEGER_CLASS_NODE = new ClassNode(Integer.class).getPlainNodeReference();
    private static final ClassNode COMPILESTATIC_CLASS_NODE = ClassHelper.make(CompileStatic.class);
    private static final ClassNode TYPECHECKINGMODE_CLASS_NODE = ClassHelper.make(TypeCheckingMode.class);
    public static final Parameter[] ZERO_PARAMETERS = new Parameter[0];
    public static final ArgumentListExpression ZERO_ARGUMENTS = new ArgumentListExpression();


    public static void warning(final SourceUnit sourceUnit, final ASTNode node, final String warningMessage) {
        final String sample = sourceUnit.getSample(node.getLineNumber(), node.getColumnNumber(), new Janitor());
        System.err.println("WARNING: " + warningMessage + "\n\n" + sample);
    }

    /**
     * Generates a fatal compilation error.
     *
     * @param sourceUnit the SourceUnit
     * @param astNode the ASTNode which caused the error
     * @param message The error message
     */
    public static void error(final SourceUnit sourceUnit, final ASTNode astNode, final String message) {
        error(sourceUnit, astNode, message, true);
    }

    /**
     * Generates a fatal compilation error.
     *
     * @param sourceUnit the SourceUnit
     * @param astNode the ASTNode which caused the error
     * @param message The error message
     * @param fatal indicates if this is a fatal error
     */
    public static void error(final SourceUnit sourceUnit, final ASTNode astNode, final String message, final boolean fatal) {
        final SyntaxException syntaxException = new SyntaxException(message, astNode.getLineNumber(), astNode.getColumnNumber());
        final SyntaxErrorMessage syntaxErrorMessage = new SyntaxErrorMessage(syntaxException, sourceUnit);
        sourceUnit.getErrorCollector().addError(syntaxErrorMessage, fatal);
    }

    /**
     * Returns whether a classNode has the specified property or not
     *
     * @param classNode    The ClassNode
     * @param propertyName The name of the property
     * @return true if the property exists in the ClassNode
     */
    public static boolean hasProperty(ClassNode classNode, String propertyName) {
        if (classNode == null || !StringUtils.hasText(propertyName)) {
            return false;
        }

        final MethodNode method = classNode.getMethod(GrailsNameUtils.getGetterName(propertyName), Parameter.EMPTY_ARRAY);
        if (method != null) return true;

        // check read-only field with setter
        if( classNode.getField(propertyName) != null && !classNode.getMethods(GrailsNameUtils.getSetterName(propertyName)).isEmpty()) {
            return true;
        }

        for (PropertyNode pn : classNode.getProperties()) {
            if (pn.getName().equals(propertyName) && !pn.isPrivate()) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasOrInheritsProperty(ClassNode classNode, String propertyName) {
        if (hasProperty(classNode, propertyName)) {
            return true;
        }

        ClassNode parent = classNode.getSuperClass();
        while (parent != null && !getFullName(parent).equals("java.lang.Object")) {
            if (hasProperty(parent, propertyName)) {
                return true;
            }
            parent = parent.getSuperClass();
        }

        return false;
    }

    /**
     * Tests whether the ClasNode implements the specified method name.
     *
     * @param classNode  The ClassNode
     * @param methodName The method name
     * @return true if it does implement the method
     */
    public static boolean implementsZeroArgMethod(ClassNode classNode, String methodName) {
        MethodNode method = classNode.getDeclaredMethod(methodName, Parameter.EMPTY_ARRAY);
        return method != null && (method.isPublic() || method.isProtected()) && !method.isAbstract();
    }

    @SuppressWarnings("rawtypes")
    public static boolean implementsOrInheritsZeroArgMethod(ClassNode classNode, String methodName, List ignoreClasses) {
        if (implementsZeroArgMethod(classNode, methodName)) {
            return true;
        }

        ClassNode parent = classNode.getSuperClass();
        while (parent != null && !getFullName(parent).equals("java.lang.Object")) {
            if (!ignoreClasses.contains(parent) && implementsZeroArgMethod(parent, methodName)) {
                return true;
            }
            parent = parent.getSuperClass();
        }
        return false;
    }

    /**
     * Gets the full name of a ClassNode.
     *
     * @param classNode The class node
     * @return The full name
     */
    public static String getFullName(ClassNode classNode) {
        return classNode.getName();
    }

    public static ClassNode getFurthestParent(ClassNode classNode) {
        ClassNode parent = classNode.getSuperClass();
        while (parent != null && !getFullName(parent).equals("java.lang.Object")) {
            classNode = parent;
            parent = parent.getSuperClass();
        }
        return classNode;
    }

    public static ClassNode getFurthestUnresolvedParent(ClassNode classNode) {
        ClassNode parent = classNode.getSuperClass();

        while (parent != null && !getFullName(parent).equals("java.lang.Object") && !parent.isResolved()) {
            classNode = parent;
            parent = parent.getSuperClass();
        }
        return classNode;
    }

    /**
     * Adds a delegate method to the target class node where the first argument
     * is to the delegate method is 'this'. In other words a method such as
     * foo(Object instance, String bar) would be added with a signature of foo(String)
     * and 'this' is passed to the delegate instance
     *
     * @param classNode The class node
     * @param delegate The expression that looks up the delegate
     * @param declaredMethod The declared method
     * @return The added method node or null if it couldn't be added
     */
    public static MethodNode addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod) {
       return addDelegateInstanceMethod(classNode, delegate, declaredMethod, null, true);
    }
    public static MethodNode addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod, AnnotationNode markerAnnotation) {
        return addDelegateInstanceMethod(classNode, delegate, declaredMethod, markerAnnotation, true);
    }
    public static MethodNode addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod, boolean thisAsFirstArgument) {
        return addDelegateInstanceMethod(classNode,delegate,declaredMethod, null, thisAsFirstArgument);
    }
    public static MethodNode addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod, AnnotationNode markerAnnotation, boolean thisAsFirstArgument) {
        return addDelegateInstanceMethod(classNode,delegate,declaredMethod, markerAnnotation, thisAsFirstArgument, null, false);
    }
    public static MethodNode addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod, AnnotationNode markerAnnotation, boolean thisAsFirstArgument, Map<String, ClassNode> genericsPlaceholders) {
        return addDelegateInstanceMethod(classNode, delegate, declaredMethod, markerAnnotation, thisAsFirstArgument, genericsPlaceholders, false); 
    }
    
    /**
     * Adds a delegate method to the target class node where the first argument
     * is to the delegate method is 'this'. In other words a method such as
     * foo(Object instance, String bar) would be added with a signature of foo(String)
     * and 'this' is passed to the delegate instance
     *
     * @param classNode The class node
     * @param delegate The expression that looks up the delegate
     * @param declaredMethod The declared method
     * @param thisAsFirstArgument Whether 'this' should be passed as the first argument to the method
     * @return The added method node or null if it couldn't be added
     */
    public static MethodNode addDelegateInstanceMethod(ClassNode classNode, Expression delegate, MethodNode declaredMethod, AnnotationNode markerAnnotation, boolean thisAsFirstArgument, Map<String, ClassNode> genericsPlaceholders, boolean noNullCheck) {
        Parameter[] parameterTypes = thisAsFirstArgument ? getRemainingParameterTypes(declaredMethod.getParameters()) : declaredMethod.getParameters();
        String methodName = declaredMethod.getName();
        if (classNode.hasDeclaredMethod(methodName, copyParameters(parameterTypes, genericsPlaceholders))) {
            return null;
        }
        ClassNode returnType = declaredMethod.getReturnType();
        String propertyName = !returnType.isPrimaryClassNode() ? GrailsNameUtils.getPropertyForGetter(methodName, returnType.getTypeClass()) : GrailsNameUtils.getPropertyForGetter(methodName);
        if (propertyName != null && parameterTypes.length == 0 && classNode.hasProperty(propertyName)) {
            return null;
        }
        propertyName = GrailsNameUtils.getPropertyForSetter(methodName);
        if (propertyName != null && parameterTypes.length == 1 && classNode.hasProperty(propertyName)) {
            return null;
        }

        BlockStatement methodBody = new BlockStatement();
        ArgumentListExpression arguments = createArgumentListFromParameters(parameterTypes, thisAsFirstArgument, genericsPlaceholders);

        returnType = replaceGenericsPlaceholders(returnType, genericsPlaceholders);

        MethodCallExpression methodCallExpression = new MethodCallExpression(delegate, methodName, arguments);
        methodCallExpression.setMethodTarget(declaredMethod);
        
        if(!noNullCheck) {
            ThrowStatement missingMethodException = createMissingMethodThrowable(classNode, declaredMethod);
            VariableExpression apiVar = addApiVariableDeclaration(delegate, declaredMethod, methodBody);
            IfStatement ifStatement = createIfElseStatementForApiMethodCall(methodCallExpression, apiVar, missingMethodException);
            methodBody.addStatement(ifStatement);
        } else {
            methodBody.addStatement(new ExpressionStatement(methodCallExpression));
        }
        
        MethodNode methodNode = new MethodNode(methodName,
                Modifier.PUBLIC, returnType, copyParameters(parameterTypes, genericsPlaceholders),
                GrailsArtefactClassInjector.EMPTY_CLASS_ARRAY, methodBody);
        copyAnnotations(declaredMethod, methodNode);
        if(shouldAddMarkerAnnotation(markerAnnotation, methodNode)) {
            methodNode.addAnnotation(markerAnnotation);
        }

        classNode.addMethod(methodNode);
        AnnotatedNodeUtils.markAsGenerated(classNode, methodNode);

        return methodNode;
    }

    private static boolean shouldAddMarkerAnnotation(AnnotationNode markerAnnotation, MethodNode methodNode) {
        return markerAnnotation != null && methodNode.getAnnotations(markerAnnotation.getClassNode()).isEmpty();
    }

    private static IfStatement createIfElseStatementForApiMethodCall(MethodCallExpression methodCallExpression, VariableExpression apiVar, ThrowStatement missingMethodException) {
        BlockStatement ifBlock = new BlockStatement();
        ifBlock.addStatement(missingMethodException);
        BlockStatement elseBlock = new BlockStatement();
        elseBlock.addStatement(new ExpressionStatement(methodCallExpression));

        return new IfStatement(new BooleanExpression(new BinaryExpression(apiVar, EQUALS_OPERATOR, NULL_EXPRESSION)),ifBlock,elseBlock);
    }

    private static VariableExpression addApiVariableDeclaration(Expression delegate, MethodNode declaredMethod, BlockStatement methodBody) {
        VariableExpression apiVar = new VariableExpression("$api_" + declaredMethod.getName(), delegate.getType());
        DeclarationExpression de = new DeclarationExpression(apiVar, ASSIGNMENT_OPERATOR, delegate);
        methodBody.addStatement(new ExpressionStatement(de));
        return apiVar;
    }

    private static ThrowStatement createMissingMethodThrowable(ClassNode classNode, MethodNode declaredMethodNode) {
        ArgumentListExpression exceptionArgs = new ArgumentListExpression();
        exceptionArgs.addExpression(new ConstantExpression(declaredMethodNode.getName()));
        exceptionArgs.addExpression(new ClassExpression(classNode));
        return new ThrowStatement(new ConstructorCallExpression(MISSING_METHOD_EXCEPTION, exceptionArgs));
    }

    /**
     * Creates an argument list from the given parameter types.
     *
     * @param parameterTypes The parameter types
     * @param thisAsFirstArgument Whether to include a reference to 'this' as the first argument
     * @param genericsPlaceholders
     *
     * @return the arguments
     */
    public static ArgumentListExpression createArgumentListFromParameters(Parameter[] parameterTypes, boolean thisAsFirstArgument, Map<String, ClassNode> genericsPlaceholders) {
        ArgumentListExpression arguments = new ArgumentListExpression();

        if (thisAsFirstArgument) {
            arguments.addExpression(new VariableExpression("this"));
        }

        for (Parameter parameterType : parameterTypes) {
            arguments.addExpression(new VariableExpression(parameterType.getName(), replaceGenericsPlaceholders(parameterType.getType(), genericsPlaceholders)));
        }
        return arguments;
    }

    /**
     * Gets the remaining parameters excluding the first parameter in the given list
     *
     * @param parameters The parameters
     * @return A new array with the first parameter removed
     */
    public static Parameter[] getRemainingParameterTypes(Parameter[] parameters) {
        if (parameters.length == 0) {
            return GrailsArtefactClassInjector.ZERO_PARAMETERS;
        }

        Parameter[] newParameters = new Parameter[parameters.length - 1];
        System.arraycopy(parameters, 1, newParameters, 0, parameters.length - 1);
        return newParameters;
    }

    /**
     * Adds a static method call to given class node that delegates to the given method
     *
     * @param classNode The class node
     * @param delegateMethod The delegate method
     * @return The added method node or null if it couldn't be added
     */
    public static MethodNode addDelegateStaticMethod(ClassNode classNode, MethodNode delegateMethod) {
        ClassExpression classExpression = new ClassExpression(delegateMethod.getDeclaringClass());
        return addDelegateStaticMethod(classExpression, classNode, delegateMethod);
    }

    /**
     * Adds a static method to the given class node that delegates to the given method
     * and resolves the object to invoke the method on from the given expression.
     *
     * @param expression The expression
     * @param classNode The class node
     * @param delegateMethod The delegate method
     * @return The added method node or null if it couldn't be added
     */
    public static MethodNode addDelegateStaticMethod(Expression expression, ClassNode classNode, MethodNode delegateMethod) {
        return addDelegateStaticMethod(expression, classNode, delegateMethod, null, null, true);
    }
        /**
         * Adds a static method to the given class node that delegates to the given method
         * and resolves the object to invoke the method on from the given expression.
         *
         * @param delegate The expression
         * @param classNode The class node
         * @param delegateMethod The delegate method
         * @param markerAnnotation A marker annotation to be added to all methods
         * @return The added method node or null if it couldn't be added
         */
    public static MethodNode addDelegateStaticMethod(Expression delegate, ClassNode classNode, MethodNode delegateMethod, AnnotationNode markerAnnotation, Map<String, ClassNode> genericsPlaceholders, boolean noNullCheck) {
        Parameter[] parameterTypes = delegateMethod.getParameters();
        String declaredMethodName = delegateMethod.getName();
        if (METHOD_MISSING_METHOD_NAME.equals(declaredMethodName)) {
            declaredMethodName = STATIC_METHOD_MISSING_METHOD_NAME;
        }
        if (classNode.hasDeclaredMethod(declaredMethodName, copyParameters(parameterTypes, genericsPlaceholders))) {
            return null;
        }

        BlockStatement methodBody = new BlockStatement();
        ArgumentListExpression arguments = createArgumentListFromParameters(parameterTypes, false, genericsPlaceholders);
        
        MethodCallExpression methodCallExpression = new MethodCallExpression(
                delegate, delegateMethod.getName(), arguments);
        methodCallExpression.setMethodTarget(delegateMethod);

        if(!noNullCheck && !(delegate instanceof ClassExpression)) {
            ThrowStatement missingMethodException = createMissingMethodThrowable(classNode, delegateMethod);
            VariableExpression apiVar = addApiVariableDeclaration(delegate, delegateMethod, methodBody);
            IfStatement ifStatement = createIfElseStatementForApiMethodCall(methodCallExpression, apiVar, missingMethodException);
            methodBody.addStatement(ifStatement);
        } else {
            methodBody.addStatement(new ExpressionStatement(methodCallExpression));
        }
        
        ClassNode returnType = replaceGenericsPlaceholders(delegateMethod.getReturnType(), genericsPlaceholders);
        MethodNode methodNode = new MethodNode(declaredMethodName, Modifier.PUBLIC | Modifier.STATIC, returnType,
                copyParameters(parameterTypes, genericsPlaceholders), GrailsArtefactClassInjector.EMPTY_CLASS_ARRAY,
                methodBody);
        copyAnnotations(delegateMethod, methodNode);
        if (shouldAddMarkerAnnotation(markerAnnotation, methodNode)) {
            methodNode.addAnnotation(markerAnnotation);
        }

        classNode.addMethod(methodNode);
        return AnnotatedNodeUtils.markAsGenerated(classNode, methodNode);
    }

    /**
     * Adds or modifies an existing constructor to delegate to the
     * given static constructor method for initialization logic.
     *
     * @param classNode The class node
     * @param constructorMethod The constructor static method
     */
    public static ConstructorNode addDelegateConstructor(ClassNode classNode, MethodNode constructorMethod, Map<String, ClassNode> genericsPlaceholders) {
        BlockStatement constructorBody = new BlockStatement();
        Parameter[] constructorParams = getRemainingParameterTypes(constructorMethod.getParameters());
        ArgumentListExpression arguments = createArgumentListFromParameters(constructorParams, true, genericsPlaceholders);
        MethodCallExpression constructCallExpression = new MethodCallExpression(
                new ClassExpression(constructorMethod.getDeclaringClass()), "initialize", arguments);
        constructCallExpression.setMethodTarget(constructorMethod);
        ExpressionStatement constructorInitExpression = new ExpressionStatement(constructCallExpression);
        if (constructorParams.length > 0) {
            constructorBody.addStatement(new ExpressionStatement(new ConstructorCallExpression(ClassNode.THIS, GrailsArtefactClassInjector.ZERO_ARGS)));
        }
        constructorBody.addStatement(constructorInitExpression);

        if (constructorParams.length == 0) {
            // handle default constructor

            ConstructorNode constructorNode = getDefaultConstructor(classNode);
            if (constructorNode != null) {
                List<AnnotationNode> annotations = constructorNode.getAnnotations(new ClassNode(GrailsDelegatingConstructor.class));
                if (annotations.size() == 0) {
                    Statement existingBodyCode = constructorNode.getCode();
                    if (existingBodyCode instanceof BlockStatement) {
                        ((BlockStatement) existingBodyCode).addStatement(constructorInitExpression);
                    }
                    else {
                        constructorNode.setCode(constructorBody);
                    }
                }
            } else {
                constructorNode = new ConstructorNode(Modifier.PUBLIC, constructorBody);
                classNode.addConstructor(constructorNode);
                AnnotatedNodeUtils.markAsGenerated(classNode, constructorNode);
            }
            constructorNode.addAnnotation(new AnnotationNode(new ClassNode(GrailsDelegatingConstructor.class)));
            return constructorNode;
        }
        else {
            // create new constructor, restoring default constructor if there is none
            ConstructorNode cn = findConstructor(classNode, constructorParams);
            if (cn == null) {
                cn = new ConstructorNode(Modifier.PUBLIC, copyParameters(constructorParams, genericsPlaceholders), null, constructorBody);
                classNode.addConstructor(cn);
                AnnotatedNodeUtils.markAsGenerated(classNode, cn);
            }
            else {
                List<AnnotationNode> annotations = cn.getAnnotations(new ClassNode(GrailsDelegatingConstructor.class));
                if (annotations.size() == 0) {
                    Statement code = cn.getCode();
                    constructorBody.addStatement(code);
                    cn.setCode(constructorBody);
                }
            }

            ConstructorNode defaultConstructor = getDefaultConstructor(classNode);
            if (defaultConstructor == null) {
                // add empty
                ConstructorNode constructorNode = new ConstructorNode(Modifier.PUBLIC, new BlockStatement());
                classNode.addConstructor(constructorNode);
                AnnotatedNodeUtils.markAsGenerated(classNode, constructorNode);
            }
            cn.addAnnotation(new AnnotationNode(new ClassNode(GrailsDelegatingConstructor.class)));
            return cn;
        }
    }

    /**
     * Finds a constructor for the given class node and parameter types
     *
     * @param classNode The class node
     * @param constructorParams The parameter types
     * @return The located constructor or null
     */
    public static ConstructorNode findConstructor(ClassNode classNode,Parameter[] constructorParams) {
        List<ConstructorNode> declaredConstructors = classNode.getDeclaredConstructors();
        for (ConstructorNode declaredConstructor : declaredConstructors) {
            if (parametersEqual(constructorParams, declaredConstructor.getParameters())) {
                return declaredConstructor;
            }
        }
        return null;
    }

    /**
     * @return true if the two arrays are of the same size and have the same contents
     */
    public static boolean parametersEqual(Parameter[] a, Parameter[] b) {
        if (a.length != b.length) {
            return false;
        }

        for (int i = 0; i < a.length; i++) {
            if (!a[i].getType().equals(b[i].getType())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Obtains the default constructor for the given class node.
     *
     * @param classNode The class node
     * @return The default constructor or null if there isn't one
     */
    public static ConstructorNode getDefaultConstructor(ClassNode classNode) {
        for (ConstructorNode cons : classNode.getDeclaredConstructors()) {
            if (cons.getParameters().length == 0) {
                return cons;
            }
        }
        return null;
    }

    public static Parameter[] copyParameters(Parameter[] parameterTypes) {
        return copyParameters(parameterTypes, null);
    }

    public static Parameter[] copyParameters(Parameter[] parameterTypes, Map<String, ClassNode> genericsPlaceholders) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i];
            Parameter newParameter = new Parameter(replaceGenericsPlaceholders(parameterType.getType(), genericsPlaceholders), parameterType.getName(), parameterType.getInitialExpression());
            copyAnnotations(parameterType, newParameter);
            newParameterTypes[i] = newParameter;
        }
        return newParameterTypes;
    }

    private static final Map<String, ClassNode> emptyGenericsPlaceHoldersMap = Collections.emptyMap();
    
    public static ClassNode nonGeneric(ClassNode type) {
        return replaceGenericsPlaceholders(type, emptyGenericsPlaceHoldersMap);
    }

    @SuppressWarnings("unchecked")
    public static ClassNode nonGeneric(ClassNode type, final ClassNode wildcardReplacement) {
        return replaceGenericsPlaceholders(type, emptyGenericsPlaceHoldersMap, wildcardReplacement);
    }
    
    public static ClassNode replaceGenericsPlaceholders(ClassNode type, Map<String, ClassNode> genericsPlaceholders) {
        return replaceGenericsPlaceholders(type, genericsPlaceholders, null);
    }
    
    public static ClassNode replaceGenericsPlaceholders(ClassNode type, Map<String, ClassNode> genericsPlaceholders, ClassNode defaultPlaceholder) {
        if (type.isArray()) {
            return replaceGenericsPlaceholders(type.getComponentType(), genericsPlaceholders).makeArray();
        }

        if (!type.isUsingGenerics() && !type.isRedirectNode()) {
            return type.getPlainNodeReference();
        }

        if(type.isGenericsPlaceHolder() && genericsPlaceholders != null) {
            final ClassNode placeHolderType;
            if(genericsPlaceholders.containsKey(type.getUnresolvedName())) {
                placeHolderType = genericsPlaceholders.get(type.getUnresolvedName());
            } else {
                placeHolderType = defaultPlaceholder;
            }
            if(placeHolderType != null) {
                return placeHolderType.getPlainNodeReference();
            } else {
                return ClassHelper.make(Object.class).getPlainNodeReference();
            }
        }

        final ClassNode nonGen = type.getPlainNodeReference();
        
        if("java.lang.Object".equals(type.getName())) {
            nonGen.setGenericsPlaceHolder(false);
            nonGen.setGenericsTypes(null);
            nonGen.setUsingGenerics(false);
        } else {
            if(type.isUsingGenerics()) {
                GenericsType[] parameterized = type.getGenericsTypes();
                if (parameterized != null && parameterized.length > 0) {
                    GenericsType[] copiedGenericsTypes = new GenericsType[parameterized.length];
                    for (int i = 0; i < parameterized.length; i++) {
                        GenericsType parameterizedType = parameterized[i];
                        GenericsType copiedGenericsType = null;
                        if (parameterizedType.isPlaceholder() && genericsPlaceholders != null) {
                            ClassNode placeHolderType = genericsPlaceholders.get(parameterizedType.getName());
                            if(placeHolderType != null) {
                                copiedGenericsType = new GenericsType(placeHolderType.getPlainNodeReference());
                            } else {
                                copiedGenericsType = new GenericsType(ClassHelper.make(Object.class).getPlainNodeReference());
                            }
                        } else {
                            copiedGenericsType = new GenericsType(replaceGenericsPlaceholders(parameterizedType.getType(), genericsPlaceholders));
                        }
                        copiedGenericsTypes[i] = copiedGenericsType;
                    }
                    nonGen.setGenericsTypes(copiedGenericsTypes);
                }
            }
        }
        
        return nonGen;
    }

    public static boolean isCandidateInstanceMethod(ClassNode classNode, MethodNode declaredMethod) {
        Parameter[] parameterTypes = declaredMethod.getParameters();
        return isCandidateMethod(declaredMethod) && parameterTypes != null &&
            parameterTypes.length > 0 && isAssignableFrom(parameterTypes[0].getType(), classNode);
    }

    /**
     * Determines if the class or interface represented by the superClass
     * argument is either the same as, or is a superclass or superinterface of,
     * the class or interface represented by the specified subClass parameter.
     *
     * @param superClass The super class to check
     * @param childClass The sub class the check
     * @return true if the childClass is either equal to or a sub class of the specified superClass
     */
    public static boolean isAssignableFrom(ClassNode superClass, ClassNode childClass) {
        ClassNode currentSuper = childClass;
        while (currentSuper != null)  {
            if (currentSuper.equals(superClass)) {
                return true;
            }

            currentSuper = currentSuper.getSuperClass();
        }
        return false;
    }


    public static boolean isSubclassOfOrImplementsInterface(ClassNode childClass, ClassNode superClass) {
        String superClassName = superClass.getName();
        return isSubclassOfOrImplementsInterface(childClass, superClassName);
    }

    public static boolean isSubclassOfOrImplementsInterface(ClassNode childClass, String superClassName) {
        return isSubclassOf(childClass, superClassName) || implementsInterface(childClass, superClassName);
    }

    private static boolean implementsInterface(ClassNode classNode, String interfaceName) {
        ClassNode currentClassNode = classNode;
        while (currentClassNode != null && !currentClassNode.getName().equals(OBJECT_CLASS)) {
            ClassNode[] interfaces = currentClassNode.getInterfaces();
            if (implementsInterfaceInternal(interfaces, interfaceName)) return true;
            currentClassNode = currentClassNode.getSuperClass();
        }
        return false;
    }

    private static boolean implementsInterfaceInternal(ClassNode[] interfaces, String interfaceName) {
        for (ClassNode anInterface : interfaces) {
            if(anInterface.getName().equals(interfaceName)) {
                return true;
            }
            ClassNode[] childInterfaces = anInterface.getInterfaces();
            if(childInterfaces != null && childInterfaces.length>0) {
                return implementsInterfaceInternal(childInterfaces,interfaceName );
            }

        }
        return false;
    }

    public static boolean isCandidateMethod(MethodNode declaredMethod) {
        return !declaredMethod.isSynthetic() &&
                !declaredMethod.getName().contains("$")
                && Modifier.isPublic(declaredMethod.getModifiers()) &&
                !Modifier.isAbstract(declaredMethod.getModifiers());
    }

    public static boolean isConstructorMethod(MethodNode declaredMethod) {
        return declaredMethod.isStatic() && declaredMethod.isPublic() &&
                declaredMethod.getName().equals("initialize") &&
                declaredMethod.getParameters().length >= 1 &&
                declaredMethod.getParameters()[0].getType().equals(AbstractGrailsArtefactTransformer.OBJECT_CLASS);
    }

    public static boolean isDomainClass(final ClassNode classNode, final SourceUnit sourceNode) {
        @SuppressWarnings("unchecked")
        boolean isDomainClass = GrailsASTUtils.hasAnyAnnotations(classNode,
                grails.persistence.Entity.class,
                jakarta.persistence.Entity.class);

        if (!isDomainClass && sourceNode != null) {
            final String sourcePath = sourceNode.getName();
            final String grailsAppDirToLookFor = File.separator +
                    GRAILS_APP_DIR + File.separator;
            final int indexOfGrailsAppDir = sourcePath.lastIndexOf(grailsAppDirToLookFor);
            if(indexOfGrailsAppDir >= 0) {
                final String pathToGrailsAppDir =
                        sourcePath.substring(0, indexOfGrailsAppDir +
                                                grailsAppDirToLookFor.length());

                final String pathToDomainDir = pathToGrailsAppDir +
                        DOMAIN_DIR + File.separator;

                final String className = classNode.getName();
                final String relativePathToDomainSourceFile =
                        className.replace('.', File.separatorChar) + ".groovy";
                final String pathToDomainSourceFile = pathToDomainDir +
                                                      relativePathToDomainSourceFile;

                isDomainClass = new File(pathToDomainSourceFile).exists();
            }
        }

        return isDomainClass;
    }

    public static void addDelegateInstanceMethods(ClassNode classNode, ClassNode delegateNode, Expression delegateInstance) {
        addDelegateInstanceMethods(classNode, delegateNode, delegateInstance, null);
    }

    public static void addDelegateInstanceMethods(ClassNode classNode, ClassNode delegateNode, Expression delegateInstance, Map<String, ClassNode> genericsPlaceholders) {
        addDelegateInstanceMethods(classNode, classNode, delegateNode, delegateInstance, genericsPlaceholders, false, false);
    }

    public static void addDelegateInstanceMethods(ClassNode supportedSuperType, ClassNode classNode, ClassNode delegateNode, Expression delegateInstance) {
        addDelegateInstanceMethods(supportedSuperType, classNode, delegateNode, delegateInstance, null, false, false);
    }

    public static void addDelegateInstanceMethods(ClassNode supportedSuperType, ClassNode classNode, ClassNode delegateNode, Expression delegateInstance, Map<String, ClassNode> genericsPlaceholders, boolean noNullCheck, boolean addCompileStatic) {
        while (!delegateNode.equals(AbstractGrailsArtefactTransformer.OBJECT_CLASS)) {
            List<MethodNode> declaredMethods = delegateNode.getMethods();
            for (MethodNode declaredMethod : declaredMethods) {

                if (isConstructorMethod(declaredMethod)) {
                    addDelegateConstructor(classNode, declaredMethod, genericsPlaceholders);
                }
                else if (isCandidateInstanceMethod(supportedSuperType, declaredMethod)) {
                    MethodNode methodNode = addDelegateInstanceMethod(classNode, delegateInstance, declaredMethod, null, true, genericsPlaceholders, noNullCheck);
                    if(addCompileStatic) {
                        addCompileStaticAnnotation(methodNode);
                    }
                }
            }
            delegateNode = delegateNode.getSuperClass();
        }
    }

    public static FieldNode addFieldIfNonExistent(ClassNode classNode, ClassNode fieldType, String fieldName) {
        if (classNode != null && classNode.getField(fieldName) == null) {
            return classNode.addField(fieldName, Modifier.PRIVATE, fieldType,
                    new ConstructorCallExpression(fieldType, new ArgumentListExpression()));
        }
        return null;
    }


    /**
     * Adds the given expression as a member of the given annotation
     *
     * @param annotationNode The annotation node
     * @param memberName The name of the member
     * @param expression The expression
     */
    public static void addExpressionToAnnotationMember(AnnotationNode annotationNode, String memberName, Expression expression) {
        Expression exclude = annotationNode.getMember(memberName);
        if(exclude instanceof ListExpression) {
            ((ListExpression)exclude).addExpression(expression);
        }
        else if(exclude != null) {
            ListExpression list = new ListExpression();
            list.addExpression(exclude);
            list.addExpression(expression);
            annotationNode.setMember(memberName, list);
        }
        else {
            annotationNode.setMember(memberName, expression);
        }
    }

    /**
     * Adds an annotation to the give nclass node if it doesn't already exist
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    public static void addAnnotationIfNecessary(ClassNode classNode, Class<? extends Annotation> annotationClass) {
        addAnnotationOrGetExisting(classNode, annotationClass);
    }

    /**
     * Adds an annotation to the given class node or returns the existing annotation
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    public static AnnotationNode addAnnotationOrGetExisting(ClassNode classNode, Class<? extends Annotation> annotationClass) {
        return addAnnotationOrGetExisting(classNode, annotationClass, Collections.<String, Object>emptyMap());
    }

    /**
     * Adds an annotation to the given class node or returns the existing annotation
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    public static AnnotationNode addAnnotationOrGetExisting(ClassNode classNode, Class<? extends Annotation> annotationClass, Map<String, Object> members) {
        ClassNode annotationClassNode = ClassHelper.make(annotationClass);
        return addAnnotationOrGetExisting(classNode, annotationClassNode, members);
    }

    public static AnnotationNode addAnnotationOrGetExisting(ClassNode classNode, ClassNode annotationClassNode) {
        return addAnnotationOrGetExisting(classNode, annotationClassNode, Collections.<String, Object>emptyMap());
    }

    public static AnnotationNode addAnnotationOrGetExisting(ClassNode classNode, ClassNode annotationClassNode, Map<String, Object> members) {
        List<AnnotationNode> annotations = classNode.getAnnotations();
        AnnotationNode annotationToAdd = new AnnotationNode(annotationClassNode);
        if (annotations.isEmpty()) {
            classNode.addAnnotation(annotationToAdd);
        }
        else {
            AnnotationNode existing = findAnnotation(annotationClassNode, annotations);
            if (existing != null){
                annotationToAdd = existing;
            }
            else {
                classNode.addAnnotation(annotationToAdd);
            }
        }

        if(members != null && !members.isEmpty()) {
            for (Map.Entry<String, Object> memberEntry : members.entrySet()) {
                Object value = memberEntry.getValue();
                annotationToAdd.setMember( memberEntry.getKey(), value instanceof Expression ? (Expression)value : new ConstantExpression(value));
            }
        }
        return annotationToAdd;
    }


    /**
     * Add the grails.artefact.Enhanced annotation to classNode if it does not already exist and ensure that
     * all of the features in the enhancedFor array are represented in the enhancedFor attribute of the
     * Enhanced annnotation
     * @param classNode the class to add the Enhanced annotation to
     * @param enhancedFor an array of feature names to include in the enhancedFor attribute of the annotation
     * @return the AnnotationNode associated with the Enhanced annotation for classNode
     * @see Enhanced
     */
    public static AnnotationNode addEnhancedAnnotation(final ClassNode classNode, final String... enhancedFor) {
        final AnnotationNode enhancedAnnotationNode;
        final List<AnnotationNode> annotations = classNode.getAnnotations(ENHANCED_CLASS_NODE);
        if (annotations.isEmpty()) {
            enhancedAnnotationNode = new AnnotationNode(ENHANCED_CLASS_NODE);
            String grailsVersion = getGrailsVersion();
            if(grailsVersion == null) {
                grailsVersion = System.getProperty("grails.version");
            }
            if(grailsVersion != null) {
                enhancedAnnotationNode.setMember("version", new ConstantExpression(grailsVersion));
            }
            classNode.addAnnotation(enhancedAnnotationNode);
        } else {
            enhancedAnnotationNode = annotations.get(0);
        }
        
        if(enhancedFor != null && enhancedFor.length > 0) {
            ListExpression enhancedForArray = (ListExpression) enhancedAnnotationNode.getMember("enhancedFor");
            if(enhancedForArray == null) {
                enhancedForArray = new ListExpression();
                enhancedAnnotationNode.setMember("enhancedFor", enhancedForArray);
            }
            final List<Expression> featureNameExpressions = enhancedForArray.getExpressions();
            for(final String feature : enhancedFor) {
                boolean exists = false;
                for(Expression expression : featureNameExpressions) {
                    if(expression instanceof ConstantExpression && feature.equals(((ConstantExpression)expression).getValue())) {
                        exists = true;
                        break;
                    }
                }
                if(!exists) {
                    featureNameExpressions.add(new ConstantExpression(feature));
                }
            }
        }

        return enhancedAnnotationNode;
    }

    private static String getGrailsVersion() {
        return GrailsASTUtils.class.getPackage().getImplementationVersion();
    }

    public static AnnotationNode findAnnotation(ClassNode annotationClassNode, List<AnnotationNode> annotations) {
        for (AnnotationNode annotation : annotations) {
            if (annotation.getClassNode().equals(annotationClassNode)) {
                return annotation;
            }
        }
        return null;
    }

    public static AnnotationNode findAnnotation(ClassNode classNode, Class<?> type) {
        List<AnnotationNode> annotations = classNode.getAnnotations();
        return annotations == null ? null : findAnnotation(new ClassNode(type),annotations);
    }

    /**
     * Returns true if classNode is marked with annotationClass
     * @param classNode A ClassNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    public static boolean hasAnnotation(final ClassNode classNode, final Class<? extends Annotation> annotationClass) {
        return !classNode.getAnnotations(new ClassNode(annotationClass)).isEmpty();
    }

    /**
     * Returns true if MethodNode is marked with annotationClass
     * @param methodNode A MethodNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    public static boolean hasAnnotation(final MethodNode methodNode, final Class<? extends Annotation> annotationClass) {
        return !methodNode.getAnnotations(new ClassNode(annotationClass)).isEmpty();
    }

    /**
     * @param classNode a ClassNode to search
     * @param annotationsToLookFor Annotations to look for
     * @return true if classNode is marked with any of the annotations in annotationsToLookFor
     */
    public static boolean hasAnyAnnotations(final ClassNode classNode, final Class<? extends Annotation>... annotationsToLookFor) {
        for (Class<? extends Annotation> annotationClass : annotationsToLookFor) {
            if(hasAnnotation(classNode, annotationClass)) {
                return true;
            }
        }
        return false;
    }

    public static boolean removeAnnotation(final MethodNode methodNode, final Class<? extends Annotation> annotationClass) {
        List<AnnotationNode> annotations = methodNode.getAnnotations(new ClassNode(annotationClass));
        if (annotations.size() > 0) {
            methodNode.getAnnotations().removeAll(annotations);
            return true;
        } else {
            return false;
        }
    }

    public static void addMethodIfNotPresent(ClassNode controllerClassNode, MethodNode methodNode) {
        MethodNode existing = controllerClassNode.getMethod(methodNode.getName(), methodNode.getParameters());
        if (existing == null) {
            controllerClassNode.addMethod(methodNode);
            AnnotatedNodeUtils.markAsGenerated(controllerClassNode, methodNode);
        }
    }

    public static ExpressionStatement createPrintlnStatement(String message) {
        return new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"),"println", new ArgumentListExpression(new ConstantExpression(message))));
    }

    public static ExpressionStatement createPrintlnStatement(String message, String variable) {
        return new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"),"println", new ArgumentListExpression(new BinaryExpression(new ConstantExpression(message),Token.newSymbol(Types.PLUS, 0, 0),new VariableExpression(variable)))));
    }

    /**
     * Wraps a method body in try / catch logic that catches any errors and logs an error, but does not rethrow!
     *
     * @param methodNode The method node
     */
    public static void wrapMethodBodyInTryCatchDebugStatements(MethodNode methodNode) {
        BlockStatement code = (BlockStatement) methodNode.getCode();
        BlockStatement newCode = new BlockStatement();
        TryCatchStatement tryCatchStatement = new TryCatchStatement(code, new BlockStatement());
        newCode.addStatement(tryCatchStatement);
        methodNode.setCode(newCode);
        BlockStatement catchBlock = new BlockStatement();
        ArgumentListExpression logArguments = new ArgumentListExpression();
        logArguments.addExpression(new BinaryExpression(new ConstantExpression("Error initializing class: "),Token.newSymbol(Types.PLUS, 0, 0),new VariableExpression("e")));
        logArguments.addExpression(new VariableExpression("e"));
        catchBlock.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("log"), "error", logArguments)));
        tryCatchStatement.addCatch(new CatchStatement(new Parameter(new ClassNode(Throwable.class), "e"),catchBlock));
    }

    /**
     * Evaluates a constraints closure and returns metadata about the constraints configured in the closure. The
     * {@link Map} returned has {@link String} property names as keys and the value associated with each of those property names is
     * a {@link Map} which has {@link String} constraint names as keys and the {@link Expression} associated with that constraint
     * as values.
     *
     * @param closureExpression the closure expression to evaluate
     * @return the Map as described above
     */
    public static Map<String, Map<String, Expression>> getConstraintMetadata(final ClosureExpression closureExpression) {

        final List<MethodCallExpression> methodExpressions = new ArrayList<MethodCallExpression>();

        final Map<String, Map<String, Expression>> results = new LinkedHashMap<String, Map<String, Expression>>();
        final Statement closureCode = closureExpression.getCode();
        if (closureCode instanceof BlockStatement) {
            final List<Statement> closureStatements = ((BlockStatement) closureCode).getStatements();
            for (final Statement closureStatement : closureStatements) {
                if (closureStatement instanceof ExpressionStatement) {
                    final Expression expression = ((ExpressionStatement) closureStatement).getExpression();
                    if (expression instanceof MethodCallExpression) {
                        methodExpressions.add((MethodCallExpression) expression);
                    }
                } else if (closureStatement instanceof ReturnStatement) {
                    final ReturnStatement returnStatement = (ReturnStatement) closureStatement;
                    Expression expression = returnStatement.getExpression();
                    if (expression instanceof MethodCallExpression) {
                        methodExpressions.add((MethodCallExpression) expression);
                    }
                }

                for (final MethodCallExpression methodCallExpression : methodExpressions) {
                    final Expression objectExpression = methodCallExpression.getObjectExpression();
                    if (objectExpression instanceof VariableExpression && "this".equals(((VariableExpression)objectExpression).getName())) {
                        final Expression methodCallArguments = methodCallExpression.getArguments();
                        if (methodCallArguments instanceof TupleExpression) {
                            final List<Expression> methodCallArgumentExpressions = ((TupleExpression) methodCallArguments).getExpressions();
                            if (methodCallArgumentExpressions != null && methodCallArgumentExpressions.size() == 1 && methodCallArgumentExpressions.get(0) instanceof NamedArgumentListExpression) {
                                final Map<String, Expression> constraintNameToExpression = new LinkedHashMap<String, Expression>();
                                final List<MapEntryExpression> mapEntryExpressions = ((NamedArgumentListExpression) methodCallArgumentExpressions.get(0)).getMapEntryExpressions();
                                for (final MapEntryExpression mapEntryExpression : mapEntryExpressions) {
                                    final Expression keyExpression = mapEntryExpression.getKeyExpression();
                                    if (keyExpression instanceof ConstantExpression) {
                                        final Object value = ((ConstantExpression) keyExpression).getValue();
                                        if (value instanceof String) {
                                            constraintNameToExpression.put((String)value, mapEntryExpression.getValueExpression());
                                        }
                                    }
                                }
                                results.put(methodCallExpression.getMethodAsString(), constraintNameToExpression);
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    /**
     * Returns a map containing the names and types of the given association type. eg. GrailsDomainClassProperty.HAS_MANY
     * @param classNode The target class ndoe
     * @param associationType The associationType
     * @return A map
     */
    public static Map<String, ClassNode> getAssocationMap(ClassNode classNode, String associationType) {
        PropertyNode property = classNode.getProperty(associationType);
        Map<String, ClassNode> associationMap = new LinkedHashMap<String, ClassNode>();
        if (property != null && property.isStatic()) {
            Expression e = property.getInitialExpression();
            if (e instanceof MapExpression) {
                MapExpression me = (MapExpression) e;
                for (MapEntryExpression mee : me.getMapEntryExpressions()) {
                    String key = mee.getKeyExpression().getText();
                    Expression valueExpression = mee.getValueExpression();
                    if (valueExpression instanceof ClassExpression) {
                        associationMap.put(key, valueExpression.getType());
                    }
                }
            }
        }
        return associationMap;
    }

    public static Map<String,ClassNode> getAllAssociationMap(ClassNode classNode) {
        Map<String, ClassNode> associationMap = new LinkedHashMap<String, ClassNode>();
        associationMap.putAll( getAssocationMap(classNode, GormProperties.HAS_MANY));
        associationMap.putAll( getAssocationMap(classNode, GormProperties.HAS_ONE));
        associationMap.putAll( getAssocationMap(classNode, GormProperties.BELONGS_TO));
        return associationMap;
    }

    public static ClassNode findInterface(ClassNode classNode, ClassNode interfaceNode) {
        while(!classNode.equals(OBJECT_CLASS_NODE)) {

            Set<ClassNode> interfaces = classNode.getAllInterfaces();

            for (ClassNode anInterface : interfaces) {
                if(anInterface.equals(interfaceNode)) return anInterface;

            }
            classNode = classNode.getSuperClass();
        }
        return null;
    }

    public static boolean hasZeroArgsConstructor(ClassNode implementationNode) {
        List<ConstructorNode> constructors = implementationNode.getDeclaredConstructors();
        if(constructors.isEmpty()) return true;
        for (ConstructorNode constructor : constructors) {
            if(constructor.getParameters().length == 0 ) return true;
        }
        return false;
    }
    /**
     * Whether the given class node is an inner class
     *
     * @param classNode The class node
     * @return True if it is
     */
    public static boolean isInnerClassNode(ClassNode classNode) {
        return (classNode instanceof InnerClassNode) || classNode.getName().contains("$");
    }

    /**
     * Returns true if the given class name is a parent class of the given class
     *
     * @param classNode The class node
     * @param parentClassName the parent class name
     * @return True if it is a subclass
     */
    public static boolean isSubclassOf(ClassNode classNode, String parentClassName) {
        ClassNode currentSuper = classNode.getSuperClass();
        while (currentSuper != null && !currentSuper.getName().equals(OBJECT_CLASS)) {
            if (currentSuper.getName().equals(parentClassName)) return true;
            currentSuper = currentSuper.getSuperClass();
        }
        return false;
    }

    @Target(ElementType.CONSTRUCTOR)
    @Retention(RetentionPolicy.SOURCE)
    private static @interface GrailsDelegatingConstructor {}
    
    /**
     * Marks a method to be staticly compiled
     * 
     * @param annotatedNode
     * @return The annotated method
     */
    public static AnnotatedNode addCompileStaticAnnotation(AnnotatedNode annotatedNode) {
        return addCompileStaticAnnotation(annotatedNode, false);
    }
    
    /**
     * Adds @CompileStatic annotation to method
     * 
     * @param annotatedNode
     * @param skip
     * @return The annotated method
     */
    public static AnnotatedNode addCompileStaticAnnotation(AnnotatedNode annotatedNode, boolean skip) {
        if(annotatedNode != null) {
            AnnotationNode an = new AnnotationNode(COMPILESTATIC_CLASS_NODE);
            if(skip) {
                an.addMember("value", new PropertyExpression(new ClassExpression(TYPECHECKINGMODE_CLASS_NODE), "SKIP")); 
            }
            annotatedNode.addAnnotation(an);
            if(!skip) {
                annotatedNode.getDeclaringClass().addTransform(StaticCompileTransformation.class, an);
            }
        }
        return annotatedNode;
    }
    
    /**
     * Set the method target of a MethodCallExpression to the first matching method with same number of arguments.
     * This doesn't check argument types.
     * 
     * @param methodCallExpression
     * @param targetClassNode
     */
    public static MethodCallExpression applyDefaultMethodTarget(final MethodCallExpression methodCallExpression, final ClassNode targetClassNode) {
        return applyMethodTarget(methodCallExpression, targetClassNode, (ClassNode[])null);
    }
    
    /**
     * Set the method target of a MethodCallExpression to the first matching method with same number of arguments.
     * This doesn't check argument types.
     * 
     * @param methodCallExpression
     * @param targetClass
     * @return The method call expression
     */
    public static MethodCallExpression applyDefaultMethodTarget(final MethodCallExpression methodCallExpression, final Class<?> targetClass) {
        return applyDefaultMethodTarget(methodCallExpression, ClassHelper.make(targetClass).getPlainNodeReference());
    }
    
    /**
     * Set the method target of a MethodCallExpression to the first matching method with same number and type of arguments.
     * 
     * A null parameter type will match any type
     * 
     * @param methodCallExpression
     * @param targetClassNode
     * @param targetParameterTypes
     * @return The method call expression
     */
    public static MethodCallExpression applyMethodTarget(final MethodCallExpression methodCallExpression, final ClassNode targetClassNode, final ClassNode... targetParameterTypes) {
        String methodName = methodCallExpression.getMethodAsString();
        if(methodName==null) return methodCallExpression;
        int argumentCount = methodCallExpression.getArguments() != null ? ((TupleExpression)methodCallExpression.getArguments()).getExpressions().size() : 0;
        
        String methodFoundInClass = null;
        
        
        for (MethodNode method : targetClassNode.getMethods(methodName)) {
            int methodParameterCount = method.getParameters() != null ? method.getParameters().length : 0;
            if (methodParameterCount == argumentCount && (targetParameterTypes == null || (parameterTypesMatch(method.getParameters(), targetParameterTypes)))) {
                String methodFromClass = method.getDeclaringClass().getName();
                if(methodFoundInClass == null) {
                    methodCallExpression.setMethodTarget(method);
                    methodFoundInClass = methodFromClass;
                } else if (methodFromClass.equals(methodFoundInClass)) {
                    throw new RuntimeException("Multiple methods with same name '" + methodName + "' and argument count (" + argumentCount + ") in " + targetClassNode.getName() + ". Cannot apply default method target.");
                }
            }
        }
        return methodCallExpression;
    }
    
    /**
     * Set the method target of a MethodCallExpression to the first matching method with same number and type of arguments.
     * 
     * @param methodCallExpression
     * @param targetClass
     * @param targetParameterClassTypes
     * @return The method call expression
     */
    public static MethodCallExpression applyMethodTarget(final MethodCallExpression methodCallExpression, final Class<?> targetClass, final Class<?>... targetParameterClassTypes) {
        return applyMethodTarget(methodCallExpression, ClassHelper.make(targetClass).getPlainNodeReference(), convertTargetParameterTypes(targetParameterClassTypes));
    }
    
    /**
     * Set the method target of a MethodCallExpression to the first matching method with same number and type of arguments.
     * 
     * @param methodCallExpression
     * @param targetClassNode
     * @param targetParameterClassTypes
     * @return The method call expression
     */
    public static MethodCallExpression applyMethodTarget(final MethodCallExpression methodCallExpression, final ClassNode targetClassNode, final Class<?>... targetParameterClassTypes) {
        return applyMethodTarget(methodCallExpression, targetClassNode, convertTargetParameterTypes(targetParameterClassTypes));
    }    

    private static ClassNode[] convertTargetParameterTypes(final Class<?>[] targetParameterClassTypes) {
        ClassNode[] targetParameterTypes = null;
        if(targetParameterClassTypes != null) {
            targetParameterTypes = new ClassNode[targetParameterClassTypes.length];
            for(int i=0;i < targetParameterClassTypes.length;i++) {
                targetParameterTypes[i] = targetParameterClassTypes[i] != null ? ClassHelper.make(targetParameterClassTypes[i]).getPlainNodeReference() : null;
            }
        }
        return targetParameterTypes;
    }

    private static boolean parameterTypesMatch(Parameter[] parameters, ClassNode[] targetParameterTypes) {
        if(targetParameterTypes==null || targetParameterTypes.length==0) return true;
        for(int i=0;i < parameters.length;i++) {
            if(targetParameterTypes.length > i && targetParameterTypes[i] != null && !parameters[i].getType().getName().equals(targetParameterTypes[i].getName())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Build static direct call to getter of a property
     * 
     * @param objectExpression
     * @param propertyName
     * @param targetClassNode
     * @return The method call expression
     */
    public static MethodCallExpression buildGetPropertyExpression(final Expression objectExpression, final String propertyName, final ClassNode targetClassNode) {
        return buildGetPropertyExpression(objectExpression, propertyName, targetClassNode, false);
    }
    
    /**
     * Build static direct call to getter of a property
     * 
     * @param objectExpression
     * @param propertyName
     * @param targetClassNode
     * @param useBooleanGetter
     * @return The method call expression
     */
    public static MethodCallExpression buildGetPropertyExpression(final Expression objectExpression, final String propertyName, final ClassNode targetClassNode, final boolean useBooleanGetter) {
        String methodName = (useBooleanGetter ? "is" : "get") + MetaClassHelper.capitalize(propertyName);
        MethodCallExpression methodCallExpression = new MethodCallExpression(objectExpression, methodName, MethodCallExpression.NO_ARGUMENTS);
        MethodNode getterMethod = targetClassNode.getGetterMethod(methodName);
        if(getterMethod != null) {
            methodCallExpression.setMethodTarget(getterMethod);
        }
        return methodCallExpression;
    }
    
    /**
     * Build static direct call to setter of a property
     * 
     * @param objectExpression
     * @param propertyName
     * @param targetClassNode
     * @param valueExpression
     * @return The method call expression
     */
    public static MethodCallExpression buildSetPropertyExpression(final Expression objectExpression, final String propertyName, final ClassNode targetClassNode, final Expression valueExpression) {
        String methodName = "set" + MetaClassHelper.capitalize(propertyName);
        MethodCallExpression methodCallExpression = new MethodCallExpression(objectExpression, methodName, new ArgumentListExpression(valueExpression));
        MethodNode setterMethod = targetClassNode.getSetterMethod(methodName);
        if(setterMethod != null) {
            methodCallExpression.setMethodTarget(setterMethod);
        }
        return methodCallExpression;
    }
    
    /**
     * Build static direct call to put entry in Map
     * 
     * @param objectExpression
     * @param keyName
     * @param valueExpression
     * @return The method call expression
     */
    public static MethodCallExpression buildPutMapExpression(final Expression objectExpression, final String keyName, final Expression valueExpression) {
        return applyDefaultMethodTarget(new MethodCallExpression(objectExpression, "put", new ArgumentListExpression(new ConstantExpression(keyName), valueExpression)), Map.class);
    }

    /**
     * Build static direct call to get entry from Map
     * 
     * @param objectExpression
     * @param keyName
     * @return The method call expression
     */
    public static MethodCallExpression buildGetMapExpression(final Expression objectExpression, final String keyName) {
        return applyDefaultMethodTarget(new MethodCallExpression(objectExpression, "get", new ArgumentListExpression(new ConstantExpression(keyName))), Map.class);
    }
    
    public static Expression buildGetThisObjectExpression(boolean inClosureBlock) {
        if (!inClosureBlock) {
            return buildThisExpression();
        } else {
            return buildGetPropertyExpression(buildThisExpression(), "thisObject", ClassHelper.make(Closure.class).getPlainNodeReference());
        }
    }

    public static Expression buildThisExpression() {
        return new VariableExpression("this");
    }
    
    public static MethodCallExpression noImplicitThis(MethodCallExpression methodCallExpression) {
        return applyImplicitThis(methodCallExpression, false);
    }
    
    public static MethodCallExpression applyImplicitThis(MethodCallExpression methodCallExpression, boolean useImplicitThis) {
        methodCallExpression.setImplicitThis(useImplicitThis);
        return methodCallExpression;
    }

    public static void copyAnnotations(final AnnotatedNode from, final AnnotatedNode to) {
        copyAnnotations(from, to, null, null);
    }
     
    public static void copyAnnotations(final AnnotatedNode from, final AnnotatedNode to, final Set<String> included, final Set<String> excluded) {
        final List<AnnotationNode> annotationsToCopy = from.getAnnotations();
        for(final AnnotationNode node : annotationsToCopy) {
            String annotationClassName = node.getClassNode().getName();
            if((excluded==null || !excluded.contains(annotationClassName)) &&
               (included==null || included.contains(annotationClassName))) {
                final AnnotationNode copyOfAnnotationNode = cloneAnnotation(node);
                to.addAnnotation(copyOfAnnotationNode);
            }
        }
    }

    public static AnnotationNode cloneAnnotation(final AnnotationNode node) {
        final AnnotationNode copyOfAnnotationNode = new AnnotationNode(node.getClassNode());
        final Map<String, Expression> members = node.getMembers();
        for(final Map.Entry<String, Expression> entry : members.entrySet()) {
            copyOfAnnotationNode.addMember(entry.getKey(), entry.getValue());
        }
        return copyOfAnnotationNode;
    }
    
    public static void filterAnnotations(final AnnotatedNode annotatedNode, final Set<String> classNamesToRetain, final Set<String> classNamesToRemove) {
        for(Iterator<AnnotationNode> iterator = annotatedNode.getAnnotations().iterator(); iterator.hasNext(); ) {
            final AnnotationNode node = iterator.next();
            String annotationClassName = node.getClassNode().getName();
            if((classNamesToRemove==null || classNamesToRemove.contains(annotationClassName)) &&
               (classNamesToRetain==null || !classNamesToRetain.contains(annotationClassName))) {
                iterator.remove();
            }
        }
    }
    
    public static void removeCompileStaticAnnotations(final AnnotatedNode annotatedNode) {
        filterAnnotations(annotatedNode, null, new LinkedHashSet<String>(Arrays.asList(new String[]{CompileStatic.class.getName(), TypeChecked.class.getName()})));
    }
    
    public static void markApplied(ASTNode astNode, Class<?> transformationClass) {
        resolveRedirect(astNode).setNodeMetaData(appliedTransformationKey(transformationClass), Boolean.TRUE);
    }

    private static ASTNode resolveRedirect(ASTNode astNode) {
        if(astNode instanceof ClassNode) {
            astNode = ((ClassNode)astNode).redirect();
        }
        return astNode;
    }
    
    private static String appliedTransformationKey(Class<?> transformationClass) {
        return "APPLIED_" + transformationClass.getName();
    }
    
    public static boolean isApplied(ASTNode astNode, Class<?> transformationClass) {
        return resolveRedirect(astNode).getNodeMetaData(appliedTransformationKey(transformationClass)) == Boolean.TRUE;
    }
    
    public static void processVariableScopes(SourceUnit source, ClassNode classNode) {
        processVariableScopes(source, classNode, null);
    }
    
    public static void processVariableScopes(SourceUnit source, ClassNode classNode, MethodNode methodNode) {
        VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(source);
        if(methodNode == null) {
            scopeVisitor.visitClass(classNode);
        } else {
            scopeVisitor.prepareVisit(classNode);
            scopeVisitor.visitMethod(methodNode);
        }
    }

    public static boolean isGetterMethod(MethodNode md) {
        String methodName = md.getName();

        return (((methodName.startsWith("get") && methodName.length() > 3) || (methodName.startsWith("is") && methodName.length() > 2)) && (md.getParameters()==null || md.getParameters().length == 0));
    }

    public static boolean isSetterMethod(MethodNode md) {
        String methodName = md.getName();

        return ((methodName.startsWith("set") &&
                methodName.length() > 3) &&
                Character.isUpperCase(methodName.substring(3).charAt(0)) &&
                md.getParameters() != null &&
                md.getParameters().length == 1);
    }

    public static boolean isSetterOrGetterMethod(MethodNode md) {
        return isGetterMethod(md) || isSetterMethod(md);
    }

    /**
     * Find URL of SourceUnit
     *
     * source.getSource().getURI() fails in Groovy-Eclipse compiler
     *
     * @param source
     * @return URL of SourceUnit
     */
    public static URL getSourceUrl(SourceUnit source) {
        URL url = null;
        final String filename = source.getName();
        if(filename==null) {
            return null;
        }

        Resource resource = new FileSystemResource(filename);
        if (resource.exists()) {
            try {
                url = resource.getURL();
            } catch (IOException e) {
                // ignore
            }
        }
        return url;
    }

    public static URL getSourceUrl(ClassNode classNode) {
        return getSourceUrl(classNode.getModule().getContext());
    }

    public static boolean hasParameters(MethodNode methodNode) {
        return methodNode.getParameters().length > 0;
    }

    public static boolean isInheritedFromTrait(MethodNode methodNode) {
        return hasAnnotation(methodNode, Traits.TraitBridge.class);
    }
}
