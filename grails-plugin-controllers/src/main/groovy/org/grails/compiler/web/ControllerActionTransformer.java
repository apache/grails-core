/*
 * Copyright 2011-2024 the original author or authors.
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
package org.grails.compiler.web;

import grails.artefact.Artefact;
import grails.artefact.controller.support.AllowedMethodsHelper;
import grails.compiler.DelegatingMethod;
import grails.compiler.ast.AnnotatedClassInjector;
import grails.compiler.ast.AstTransformer;
import grails.compiler.ast.GrailsArtefactClassInjector;
import grails.util.CollectionUtils;
import grails.util.TypeConvertingMap;
import grails.validation.Validateable;
import grails.web.Action;
import grails.web.RequestParameter;
import grails.web.controllers.ControllerMethod;
import groovy.lang.Closure;
import groovy.transform.CompilationUnitAware;
import org.apache.groovy.ast.tools.ClassNodeUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
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
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.trait.Traits;
import org.grails.compiler.injection.GrailsASTUtils;
import org.grails.compiler.injection.TraitInjectionUtils;
import org.grails.core.DefaultGrailsControllerClass;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.plugins.web.controllers.DefaultControllerExceptionHandlerMetaData;
import org.grails.web.databinding.DefaultASTDatabindingHelper;
import org.grails.web.util.WebUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.grails.compiler.injection.GrailsASTUtils.applyDefaultMethodTarget;
import static org.grails.compiler.injection.GrailsASTUtils.applyMethodTarget;
import static org.grails.compiler.injection.GrailsASTUtils.buildGetMapExpression;
import static org.grails.compiler.injection.GrailsASTUtils.buildGetPropertyExpression;
import static org.grails.compiler.injection.GrailsASTUtils.buildSetPropertyExpression;
import static org.grails.compiler.injection.GrailsASTUtils.hasAnnotation;
import static org.grails.compiler.injection.GrailsASTUtils.hasParameters;
import static org.grails.compiler.injection.GrailsASTUtils.isInheritedFromTrait;
import static org.grails.compiler.injection.GrailsASTUtils.removeAnnotation;

/**
 * Enhances controller classes by converting closures actions to method actions and binding
 * request parameters to action arguments.
 */
/*

class TestController{

    //default  scope configurable in Config.groovy
    static scope = 'singleton'

    def peterTheFrenchService

    //--------------------------
    //allow use of methods as actions
    def someAction() {
            render 'ata'
    }

    / becomes behind the scene :
    @Action
    def someAction() {
        render 'ata'
    }
    /

    //--------------------------
    //Compile time transformed to method
    def lol2 = {
        render 'testxx'
    }

    / becomes behind the scene :
        @Action def lol2() {  render 'testxx'  }
    /

    //--------------------------

    def lol4 = { PeterCommand cmd ->
        render cmd.a
    }

    / becomes behind the scene :
        @Action(commandObjects={PeterCommand}) def lol4() {
            PeterCommand cmd = new PeterCommand(); bindData(cmd, params)
            render 'testxx'
        }
    /
}
*/

@AstTransformer
public class ControllerActionTransformer implements GrailsArtefactClassInjector, AnnotatedClassInjector, CompilationUnitAware {

    public static final AnnotationNode DELEGATING_METHOD_ANNOATION = new AnnotationNode(ClassHelper.make(DelegatingMethod.class));
    public static Pattern CONTROLLER_PATTERN = Pattern.compile(".+/" +
            GrailsResourceUtils.GRAILS_APP_DIR + "/controllers/(.+)Controller\\.groovy");
    private static final String ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME = "ALLOWED_METHODS_HANDLED";
    private static final ClassNode OBJECT_CLASS = new ClassNode(Object.class);
    public static final AnnotationNode ACTION_ANNOTATION_NODE = new AnnotationNode(
            new ClassNode(Action.class));
    private static final String ACTION_MEMBER_TARGET = "commandObjects";
    public static final String EXCEPTION_HANDLER_META_DATA_FIELD_NAME = "$exceptionHandlerMetaData";

    private static final TupleExpression EMPTY_TUPLE = new TupleExpression();
    @SuppressWarnings({"unchecked"})
    private static final Map<ClassNode, String> TYPE_WRAPPER_CLASS_TO_CONVERSION_METHOD_NAME = CollectionUtils.<ClassNode, String>newMap(
            ClassHelper.Integer_TYPE, "int",
            ClassHelper.Float_TYPE, "float",
            ClassHelper.Long_TYPE, "long",
            ClassHelper.Double_TYPE, "double",
            ClassHelper.Short_TYPE, "short",
            ClassHelper.Boolean_TYPE, "boolean",
            ClassHelper.Byte_TYPE, "byte",
            ClassHelper.Character_TYPE, "char");
    private static List<ClassNode> PRIMITIVE_CLASS_NODES = CollectionUtils.<ClassNode>newList(
            ClassHelper.boolean_TYPE,
            ClassHelper.char_TYPE,
            ClassHelper.int_TYPE,
            ClassHelper.short_TYPE,
            ClassHelper.long_TYPE,
            ClassHelper.double_TYPE,
            ClassHelper.float_TYPE,
            ClassHelper.byte_TYPE);
    public static final String VOID_TYPE = "void";

    public static final String CONVERT_CLOSURES_KEY = "grails.compile.artefacts.closures.convert";

    private Boolean converterEnabled;
    private CompilationUnit compilationUnit;

    public ControllerActionTransformer() {
        converterEnabled = Boolean.parseBoolean(System.getProperty(CONVERT_CLOSURES_KEY));
    }

    public String[] getArtefactTypes() {
        return new String[]{ControllerArtefactHandler.TYPE};
    }

    public void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        // don't inject if already an @Artefact annotation is applied
        if(!classNode.getAnnotations(new ClassNode(Artefact.class)).isEmpty()) return;

        performInjectionOnAnnotatedClass(source, context, classNode);

    }

    @Override
    public void performInjectionOnAnnotatedClass(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        final String className = classNode.getName();
        if (className.endsWith(ControllerArtefactHandler.TYPE)) {
            processMethods(classNode, source, context);
            processClosures(classNode, source, context);
        }
    }

    @Override
    public void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source,null, classNode);
    }

    private boolean isExceptionHandlingMethod(MethodNode methodNode) {
        boolean isExceptionHandler = false;
        if(!methodNode.isPrivate() && methodNode.getName().indexOf("$") == -1) {
            Parameter[] parameters = methodNode.getParameters();
            if(parameters.length == 1) {
                ClassNode parameterTypeClassNode = parameters[0].getType();
                isExceptionHandler = parameterTypeClassNode.isDerivedFrom(new ClassNode(Exception.class));
            }
        }
        return isExceptionHandler;
    }



    private void processMethods(ClassNode classNode, SourceUnit source,
            GeneratorContext context) {

        List<MethodNode> deferredNewMethods = new ArrayList<MethodNode>();
        for (MethodNode method : classNode.getMethods()) {
            if (methodShouldBeConfiguredAsControllerAction(method)) {
                final List<MethodNode> declaredMethodsWithThisName = classNode.getDeclaredMethods(method.getName());
                if(declaredMethodsWithThisName != null) {
                    final int numberOfNonExceptionHandlerMethodsWithThisName = DefaultGroovyMethods.count((Iterable)declaredMethodsWithThisName, new Closure(this) {
                        @Override
                        public Object call(Object object) {
                            return !isExceptionHandlingMethod((MethodNode) object);
                        }
                    }).intValue();
                    if (numberOfNonExceptionHandlerMethodsWithThisName > 1) {
                        String message = "Controller actions may not be overloaded.  The [" +
                                method.getName() +
                                "] action has been overloaded in [" +
                                classNode.getName() +
                                "].";
                        GrailsASTUtils.error(source, method, message);
                    }
                }
                MethodNode wrapperMethod = convertToMethodAction(classNode, method, source, context);
                if (wrapperMethod != null) {
                    deferredNewMethods.add(wrapperMethod);
                }
            }
        }
        Collection<MethodNode> exceptionHandlerMethods = getExceptionHandlerMethods(classNode, source);

        final FieldNode exceptionHandlerMetaDataField = classNode.getField(EXCEPTION_HANDLER_META_DATA_FIELD_NAME);
        if(exceptionHandlerMetaDataField == null || !exceptionHandlerMetaDataField.getDeclaringClass().equals(classNode)) {
            final ListExpression listOfExceptionHandlerMetaData = new ListExpression();
            for(final MethodNode exceptionHandlerMethod : exceptionHandlerMethods) {
                final Parameter[] parameters = exceptionHandlerMethod.getParameters();
                final Parameter firstParameter = parameters[0];
                final ClassNode firstParameterTypeClassNode = firstParameter.getType();
                final String exceptionHandlerMethodName = exceptionHandlerMethod.getName();
                final ArgumentListExpression defaultControllerExceptionHandlerMetaDataCtorArgs = new ArgumentListExpression();
                defaultControllerExceptionHandlerMetaDataCtorArgs.addExpression(new ConstantExpression(exceptionHandlerMethodName));
                defaultControllerExceptionHandlerMetaDataCtorArgs.addExpression(new ClassExpression(firstParameterTypeClassNode.getPlainNodeReference()));
                listOfExceptionHandlerMetaData.addExpression(new ConstructorCallExpression(new ClassNode(DefaultControllerExceptionHandlerMetaData.class), defaultControllerExceptionHandlerMetaDataCtorArgs));
            }
            classNode.addField(EXCEPTION_HANDLER_META_DATA_FIELD_NAME,
                    Modifier.STATIC | Modifier.PRIVATE | Modifier.FINAL, new ClassNode(List.class),
                    listOfExceptionHandlerMetaData);
        }


        for (MethodNode newMethod : deferredNewMethods) {
            ClassNodeUtils.addGeneratedMethod(classNode, newMethod);
        }
    }

    /**
     * 
     * @param method a potential controller action method
     * @return true if the method should be configured as a controller action, false otherwise
     */
    protected boolean methodShouldBeConfiguredAsControllerAction(final MethodNode method) {
        int minLineNumber = 0;
        if (isInheritedFromTrait(method) && hasAnnotation(method, Action.class) && hasParameters(method)) {
            removeAnnotation(method, Action.class);
            //Trait methods have a line number of -1
            --minLineNumber;
        }
        return !method.isStatic() && 
                method.isPublic() && 
                !method.isAbstract() &&
                method.getAnnotations(ACTION_ANNOTATION_NODE.getClassNode()).isEmpty() &&
                method.getAnnotations(new ClassNode(ControllerMethod.class)).isEmpty() &&
                method.getLineNumber() >= minLineNumber &&
                !method.getName().startsWith("$") &&
                !method.getReturnType().getName().equals(VOID_TYPE) &&
                !isExceptionHandlingMethod(method);
    }

    protected Collection<MethodNode> getExceptionHandlerMethods(final ClassNode classNode, SourceUnit sourceUnit) {
        final Map<ClassNode, MethodNode> exceptionTypeToHandlerMethodMap = new HashMap<ClassNode, MethodNode>();
        final List<MethodNode> methods = classNode.getMethods();
        for(MethodNode methodNode : methods) {
            if(isExceptionHandlingMethod(methodNode)) {
                final Parameter exceptionParameter = methodNode.getParameters()[0];
                final ClassNode exceptionType = exceptionParameter.getType();
                if(!exceptionTypeToHandlerMethodMap.containsKey(exceptionType)) {
                    exceptionTypeToHandlerMethodMap.put(exceptionType, methodNode);
                } else {
                    final MethodNode otherHandlerMethod = exceptionTypeToHandlerMethodMap.get(exceptionType);
                    final String message = "A controller may not define more than 1 exception handler for a particular exception type.  [%s] defines the [%s] and [%s] exception handlers which each accept a [%s] which is not allowed.";
                    final String formattedMessage = String.format(message, classNode.getName(), otherHandlerMethod.getName(), methodNode.getName(), exceptionType.getName());
                    GrailsASTUtils.error(sourceUnit, methodNode, formattedMessage);
                }
            }
        }
        final ClassNode superClass = classNode.getSuperClass();
        if(!superClass.equals(OBJECT_CLASS)) {
            final Collection<MethodNode> superClassMethods = getExceptionHandlerMethods(superClass, sourceUnit);
            for(MethodNode superClassMethod : superClassMethods) {
                final Parameter exceptionParameter = superClassMethod.getParameters()[0];
                final ClassNode exceptionType = exceptionParameter.getType();
                // only add this super class handler if we don't already have
                // a handler for this exception type in this class
                if(!exceptionTypeToHandlerMethodMap.containsKey(exceptionType)) {
                    exceptionTypeToHandlerMethodMap.put(exceptionType, superClassMethod);
                }
            }
        }
        return exceptionTypeToHandlerMethodMap.values();
    }

    /**
     * Converts a method into a controller action.  If the method accepts parameters,
     * a no-arg counterpart is created which delegates to the original.
     *
     * @param classNode The controller class
     * @param methodNode   The method to be converted
     * @return The no-arg wrapper method, or null if none was created.
     */
    private MethodNode convertToMethodAction(ClassNode classNode, MethodNode methodNode,
            SourceUnit source, GeneratorContext context) {

        final ClassNode returnType = methodNode.getReturnType();
        Parameter[] parameters = methodNode.getParameters();

        for (Parameter param : parameters) {
            if (param.hasInitialExpression()) {
                String paramName = param.getName();
                String methodName = methodNode.getName();
                String initialValue = param.getInitialExpression().getText();
                String methodDeclaration = methodNode.getText();
                String message = "Parameter [%s] to method [%s] has default value [%s].  " +
                        "Default parameter values are not allowed in controller action methods. ([%s])";
                String formattedMessage = String.format(message, paramName, methodName,
                        initialValue, methodDeclaration);
                GrailsASTUtils.error(source, methodNode, formattedMessage);
            }
        }

        MethodNode method = null;
        if (methodNode.getParameters().length > 0) {
            final BlockStatement methodCode = new BlockStatement();
            
            final BlockStatement codeToHandleAllowedMethods = getCodeToHandleAllowedMethods(classNode, methodNode.getName());
            final Statement codeToCallOriginalMethod = addOriginalMethodCall(methodNode, initializeActionParameters(
                    classNode, methodNode, methodNode.getName(), parameters, source, context));
            
            methodCode.addStatement(codeToHandleAllowedMethods);
            methodCode.addStatement(codeToCallOriginalMethod);

            
            method = new MethodNode(
                    methodNode.getName(),
                    Modifier.PUBLIC, returnType,
                    ZERO_PARAMETERS,
                    EMPTY_CLASS_ARRAY,
                    methodCode);
            
            GrailsASTUtils.copyAnnotations(methodNode, method);

            methodNode.addAnnotation(DELEGATING_METHOD_ANNOATION);
            annotateActionMethod(classNode, parameters, method);
            wrapMethodBodyWithExceptionHandling(classNode, method);
        } else {
            annotateActionMethod(classNode, parameters, methodNode);
        }
        
        wrapMethodBodyWithExceptionHandling(classNode, methodNode);

        return method;
    }

    private Statement addOriginalMethodCall(MethodNode methodNode, BlockStatement blockStatement) {

        if (blockStatement == null) {
            return null;
        }

        final ArgumentListExpression arguments = new ArgumentListExpression();
        for (Parameter p : methodNode.getParameters()) {
            arguments.addExpression(new VariableExpression(p.getName(), p.getType()));
        }

        MethodCallExpression callExpression = new MethodCallExpression(
                new VariableExpression("this"), methodNode.getName(), arguments);
        callExpression.setMethodTarget(methodNode);
        callExpression.setImplicitThis(false);

        blockStatement.addStatement(new ReturnStatement(callExpression));

        return blockStatement;
    }

    private boolean isCommandObjectAction(Parameter[] params) {
        return params != null && params.length > 0
                && params[0].getType() != new ClassNode(Object[].class)
                && params[0].getType() != new ClassNode(Object.class);
    }

    private void processClosures(ClassNode classNode, SourceUnit source, GeneratorContext context) {

        List<PropertyNode> propertyNodes = new ArrayList<PropertyNode>(classNode.getProperties());

        Expression initialExpression;
        ClosureExpression closureAction;

        for (PropertyNode property : propertyNodes) {
            initialExpression = property.getInitialExpression();
            if (!property.isStatic() && initialExpression != null &&
                    initialExpression.getClass().equals(ClosureExpression.class)) {
                closureAction = (ClosureExpression) initialExpression;
                if (converterEnabled) {
                    transformClosureToMethod(classNode, closureAction, property, source, context);
                } else {
                    addMethodToInvokeClosure(classNode, property, source, context);
                }
            }
        }
    }

    protected void addMethodToInvokeClosure(ClassNode controllerClassNode,
            PropertyNode closureProperty, SourceUnit source, GeneratorContext context) {

        MethodNode method = controllerClassNode.getMethod(closureProperty.getName(), ZERO_PARAMETERS);
        if (method == null || !method.getDeclaringClass().equals(controllerClassNode)) {
            ClosureExpression closureExpression = (ClosureExpression) closureProperty.getInitialExpression();
            final Parameter[] parameters = closureExpression.getParameters();
            final BlockStatement newMethodCode = initializeActionParameters(
                    controllerClassNode, closureProperty, closureProperty.getName(),
                    parameters, source, context);

            final ArgumentListExpression closureInvocationArguments = new ArgumentListExpression();
            if (parameters != null) {
                for (Parameter p : parameters) {
                    closureInvocationArguments.addExpression(new VariableExpression(p.getName()));
                }
            }

            final MethodCallExpression methodCallExpression = new MethodCallExpression(
                    closureExpression, "call", closureInvocationArguments);
            newMethodCode.addStatement(new ExpressionStatement(applyMethodTarget(methodCallExpression, Closure.class, Object.class)));

            final MethodNode methodNode = new MethodNode(closureProperty.getName(), Modifier.PUBLIC,
                    new ClassNode(Object.class), ZERO_PARAMETERS, EMPTY_CLASS_ARRAY, newMethodCode);
            wrapMethodBodyWithExceptionHandling(controllerClassNode, methodNode);
            annotateActionMethod(controllerClassNode, parameters, methodNode);
            ClassNodeUtils.addGeneratedMethod(controllerClassNode, methodNode);
        }
    }

    protected void annotateActionMethod(ClassNode controllerClassNode, final Parameter[] parameters, final MethodNode methodNode) {

        if (isCommandObjectAction(parameters)) {
            ListExpression initArray = new ListExpression();
            for (Parameter parameter : parameters) {
                initArray.addExpression(new ClassExpression(parameter.getType()));
            }
            AnnotationNode paramActionAnn = new AnnotationNode(new ClassNode(Action.class));
            paramActionAnn.setMember(ACTION_MEMBER_TARGET, initArray);
            methodNode.addAnnotation(paramActionAnn);

        } else {
            methodNode.addAnnotation(ACTION_ANNOTATION_NODE);
        }
    }

    protected BlockStatement getCodeToHandleAllowedMethods(ClassNode controllerClass, String methodName) {
        GrailsASTUtils.addEnhancedAnnotation(controllerClass, DefaultGrailsControllerClass.ALLOWED_HTTP_METHODS_PROPERTY);
        final BlockStatement checkAllowedMethodsBlock = new BlockStatement();
        
        final PropertyExpression requestPropertyExpression = new PropertyExpression(new VariableExpression("this"), "request");
        
        final FieldNode allowedMethodsField = controllerClass.getField(DefaultGrailsControllerClass.ALLOWED_HTTP_METHODS_PROPERTY);
        
        if(allowedMethodsField != null) {
            final Expression initialAllowedMethodsExpression = allowedMethodsField.getInitialExpression();
            if(initialAllowedMethodsExpression instanceof MapExpression) {
                boolean actionIsRestricted = false;
                final MapExpression allowedMethodsMapExpression = (MapExpression) initialAllowedMethodsExpression;
                final List<MapEntryExpression> allowedMethodsMapEntryExpressions = allowedMethodsMapExpression.getMapEntryExpressions();
                for(MapEntryExpression allowedMethodsMapEntryExpression : allowedMethodsMapEntryExpressions) {
                    final Expression allowedMethodsMapEntryKeyExpression = allowedMethodsMapEntryExpression.getKeyExpression();
                    if(allowedMethodsMapEntryKeyExpression instanceof ConstantExpression) {
                        final ConstantExpression allowedMethodsMapKeyConstantExpression = (ConstantExpression) allowedMethodsMapEntryKeyExpression;
                        final Object allowedMethodsMapKeyValue = allowedMethodsMapKeyConstantExpression.getValue();
                        if(methodName.equals(allowedMethodsMapKeyValue)) {
                            actionIsRestricted = true;
                            break;
                        }
                    }
                }
                if(actionIsRestricted) {
                    final PropertyExpression responsePropertyExpression = new PropertyExpression(new VariableExpression("this"), "response");
                    
                    final ArgumentListExpression isAllowedArgumentList = new ArgumentListExpression();
                    isAllowedArgumentList.addExpression(new ConstantExpression(methodName));
                    isAllowedArgumentList.addExpression(new PropertyExpression(new VariableExpression("this"), "request"));
                    isAllowedArgumentList.addExpression(new PropertyExpression(new VariableExpression("this"), DefaultGrailsControllerClass.ALLOWED_HTTP_METHODS_PROPERTY));
                    final Expression isAllowedMethodCall = new StaticMethodCallExpression(ClassHelper.make(AllowedMethodsHelper.class), "isAllowed", isAllowedArgumentList);
                    final BooleanExpression isValidRequestMethod = new BooleanExpression(isAllowedMethodCall);
                    final MethodCallExpression sendErrorMethodCall = new MethodCallExpression(responsePropertyExpression, "sendError", new ConstantExpression(WebUtils.SC_METHOD_NOT_ALLOWED));
                    final ReturnStatement returnStatement = new ReturnStatement(new ConstantExpression(null));
                    final BlockStatement blockToSendError = new BlockStatement();
                    blockToSendError.addStatement(new ExpressionStatement(sendErrorMethodCall));
                    blockToSendError.addStatement(returnStatement);
                    final IfStatement ifIsValidRequestMethodStatement = new IfStatement(isValidRequestMethod, new ExpressionStatement(new EmptyExpression()), blockToSendError);
                  
                    checkAllowedMethodsBlock.addStatement(ifIsValidRequestMethodStatement);
                }
            }
        }
        
        final ArgumentListExpression argumentListExpression = new ArgumentListExpression();
        argumentListExpression.addExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME));
        argumentListExpression.addExpression(new ConstantExpression(methodName));
        
        final Expression setAttributeMethodCall = new MethodCallExpression(requestPropertyExpression, "setAttribute", argumentListExpression);
        
        final BlockStatement codeToExecuteIfAttributeIsNotSet = new BlockStatement();
        codeToExecuteIfAttributeIsNotSet.addStatement(new ExpressionStatement(setAttributeMethodCall));
        codeToExecuteIfAttributeIsNotSet.addStatement(checkAllowedMethodsBlock);

        final BooleanExpression attributeIsSetBooleanExpression = new BooleanExpression(new MethodCallExpression(requestPropertyExpression, "getAttribute", new ArgumentListExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME))));
        final Statement ifAttributeIsAlreadySetStatement = new IfStatement(attributeIsSetBooleanExpression, new EmptyStatement(), codeToExecuteIfAttributeIsNotSet);
        
        final BlockStatement code = new BlockStatement();
        code.addStatement(ifAttributeIsAlreadySetStatement);

        return code;
    }
    /**
     * This will wrap the method body in a try catch block which does something
     * like this:
     * <pre>
     * try {
     *     // original method body here
     * } catch (Exception $caughtException) {
     *     Method $method = getExceptionHandlerMethod($caughtException.getClass())
     *     if($method) {
     *         return $method.invoke(this, $caughtException)
     *     } else {
     *         throw $caughtException
     *     }
     * }
     * </pre>
     * @param methodNode the method to add the try catch block to
     */
    protected void wrapMethodBodyWithExceptionHandling(final ClassNode controllerClassNode, final MethodNode methodNode) {
        final BlockStatement catchBlockCode = new BlockStatement();
        final String caughtExceptionArgumentName = "$caughtException";
        final Expression caughtExceptionVariableExpression = new VariableExpression(caughtExceptionArgumentName);
        final Expression caughtExceptionTypeExpression = new PropertyExpression(caughtExceptionVariableExpression, "class");
        final Expression thisExpression = new VariableExpression("this");
        final MethodCallExpression getExceptionHandlerMethodCall = new MethodCallExpression(thisExpression, "getExceptionHandlerMethodFor", caughtExceptionTypeExpression);
        applyDefaultMethodTarget(getExceptionHandlerMethodCall, controllerClassNode);

        final ClassNode reflectMethodClassNode = new ClassNode(Method.class);
        final String exceptionHandlerMethodVariableName = "$method";
        final Expression exceptionHandlerMethodExpression = new VariableExpression(exceptionHandlerMethodVariableName, new ClassNode(Method.class));
        final Expression declareExceptionHandlerMethod = new DeclarationExpression(
                new VariableExpression(exceptionHandlerMethodVariableName, reflectMethodClassNode), Token.newSymbol(Types.EQUALS, 0, 0), getExceptionHandlerMethodCall);
        final ArgumentListExpression invokeArguments = new ArgumentListExpression();
        invokeArguments.addExpression(thisExpression);
        invokeArguments.addExpression(caughtExceptionVariableExpression);
        final MethodCallExpression invokeExceptionHandlerMethodExpression = new MethodCallExpression(new VariableExpression(exceptionHandlerMethodVariableName), "invoke", invokeArguments);
        applyDefaultMethodTarget(invokeExceptionHandlerMethodExpression, reflectMethodClassNode);
        
        final Statement returnStatement = new ReturnStatement(invokeExceptionHandlerMethodExpression);
        final Statement throwCaughtExceptionStatement = new ThrowStatement(caughtExceptionVariableExpression);
        final Statement ifExceptionHandlerMethodExistsStatement = new IfStatement(new BooleanExpression(exceptionHandlerMethodExpression), returnStatement, throwCaughtExceptionStatement);
        catchBlockCode.addStatement(new ExpressionStatement(declareExceptionHandlerMethod));
        catchBlockCode.addStatement(ifExceptionHandlerMethodExistsStatement);

        final CatchStatement catchStatement = new CatchStatement(new Parameter(new ClassNode(Exception.class), caughtExceptionArgumentName), catchBlockCode);
        final Statement methodBody = methodNode.getCode();

        BlockStatement tryBlock = new BlockStatement();
        BlockStatement codeToHandleAllowedMethods = getCodeToHandleAllowedMethods(controllerClassNode, methodNode.getName());
        tryBlock.addStatement(codeToHandleAllowedMethods);
        tryBlock.addStatement(methodBody);

        final TryCatchStatement tryCatchStatement = new TryCatchStatement(tryBlock, new EmptyStatement());
        tryCatchStatement.addCatch(catchStatement);

        final ArgumentListExpression argumentListExpression = new ArgumentListExpression();
        argumentListExpression.addExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME));
        
        final PropertyExpression requestPropertyExpression = new PropertyExpression(new VariableExpression("this"), "request");
        final Expression removeAttributeMethodCall = new MethodCallExpression(requestPropertyExpression, "removeAttribute", argumentListExpression);
        
        final Expression getAttributeMethodCall = new MethodCallExpression(requestPropertyExpression, "getAttribute", new ArgumentListExpression(new ConstantExpression(ALLOWED_METHODS_HANDLED_ATTRIBUTE_NAME)));
        final VariableExpression attributeValueExpression = new VariableExpression("$allowed_methods_attribute_value", ClassHelper.make(Object.class));
        final Expression initializeAttributeValue = new DeclarationExpression(
                attributeValueExpression, Token.newSymbol(Types.EQUALS, 0, 0), getAttributeMethodCall);
        final Expression attributeValueMatchesMethodNameExpression = new BinaryExpression(new ConstantExpression(methodNode.getName()), 
                                                  Token.newSymbol(Types.COMPARE_EQUAL, 0, 0), 
                                                  attributeValueExpression);
        final Statement ifAttributeValueMatchesMethodName = 
                new IfStatement(new BooleanExpression(attributeValueMatchesMethodNameExpression), 
                                new ExpressionStatement(removeAttributeMethodCall), new EmptyStatement());

        final BlockStatement blockToRemoveAttribute = new BlockStatement();
        blockToRemoveAttribute.addStatement(new ExpressionStatement(initializeAttributeValue));
        blockToRemoveAttribute.addStatement(ifAttributeValueMatchesMethodName);
        
        final TryCatchStatement tryCatchToRemoveAttribute = new TryCatchStatement(blockToRemoveAttribute, new EmptyStatement());
        tryCatchToRemoveAttribute.addCatch(new CatchStatement(new Parameter(ClassHelper.make(Exception.class), "$exceptionRemovingAttribute"), new EmptyStatement()));

        tryCatchStatement.setFinallyStatement(tryCatchToRemoveAttribute);

        methodNode.setCode(tryCatchStatement);
    }

    protected void transformClosureToMethod(ClassNode classNode, ClosureExpression closureAction,
            PropertyNode property, SourceUnit source, GeneratorContext context) {

        final MethodNode actionMethod = new MethodNode(property.getName(),
                Modifier.PUBLIC, property.getType(), closureAction.getParameters(),
                EMPTY_CLASS_ARRAY, closureAction.getCode());

        MethodNode convertedMethod = convertToMethodAction(classNode, actionMethod, source, context);
        if (convertedMethod != null) {
            ClassNodeUtils.addGeneratedMethod(classNode, convertedMethod);
        }
        classNode.getProperties().remove(property);
        classNode.getFields().remove(property.getField());
        ClassNodeUtils.addGeneratedMethod(classNode, actionMethod);
    }

    protected BlockStatement initializeActionParameters(ClassNode classNode, ASTNode actionNode,
            String actionName, Parameter[] actionParameters, SourceUnit source,
            GeneratorContext context) {

        BlockStatement wrapper = new BlockStatement();

        ArgumentListExpression mapBindingResultConstructorArgs = new ArgumentListExpression();
        mapBindingResultConstructorArgs.addExpression(new ConstructorCallExpression(
                new ClassNode(HashMap.class), EMPTY_TUPLE));
        mapBindingResultConstructorArgs.addExpression(new ConstantExpression("controller"));
        final Expression mapBindingResultConstructorCallExpression = new ConstructorCallExpression(
                new ClassNode(MapBindingResult.class), mapBindingResultConstructorArgs);

        final Expression errorsAssignmentExpression = buildSetPropertyExpression(new VariableExpression("this", classNode), "errors", classNode, mapBindingResultConstructorCallExpression);

        wrapper.addStatement(new ExpressionStatement(errorsAssignmentExpression));

        if (actionParameters != null) {
            for (Parameter param : actionParameters) {
                initializeMethodParameter(classNode, wrapper, actionNode, actionName,
                        param, source, context);
            }
        }
        return wrapper;
    }

    protected void initializeMethodParameter(final ClassNode classNode, final BlockStatement wrapper,
            final ASTNode actionNode, final String actionName, final Parameter param,
            final SourceUnit source, final GeneratorContext context) {

        final ClassNode paramTypeClassNode = param.getType();
        final String paramName = param.getName();
        String requestParameterName = paramName;
        List<AnnotationNode> requestParameters = param.getAnnotations(
                new ClassNode(RequestParameter.class));

        //Check to see if the method was inherited from a trait
        if (actionNode instanceof MethodNode && paramName.startsWith("arg")) {
            List<AnnotationNode> traitBridges = ((MethodNode) actionNode).getAnnotations(new ClassNode(Traits.TraitBridge.class));
            if (traitBridges.size() == 1) {
                //Get the trait class this method came from
                Expression traitClass = traitBridges.get(0).getMember("traitClass");
                if (traitClass instanceof ClassExpression) {
                    ClassNode helperClass = Traits.findHelper(traitClass.getType());
                    //Look for a method in the trait helper with the name of the action
                    List<MethodNode> methods = helperClass.getMethods(actionName);
                    if (methods.size() == 1) {
                        Parameter[] parameters = methods.get(0).getParameters();
                        //Look for a parameter of index (argX) in the method.
                        //The $self is the first parameter, so arg1 == index of 1
                        int argNum = Integer.valueOf(paramName.replaceFirst("arg", ""));
                        if (parameters.length >= argNum + 1) {
                            Parameter helperParam = parameters[argNum];
                            //Set the request parameter name based off of the parameter in the trait helper method
                            requestParameterName = helperParam.getName();
                            requestParameters = helperParam.getAnnotations(new ClassNode(RequestParameter.class));
                        }
                    }
                }
            }
        }

        if (requestParameters.size() == 1) {
            requestParameterName = requestParameters.get(0).getMember("value").getText();
        }

        if ((PRIMITIVE_CLASS_NODES.contains(paramTypeClassNode) ||
                TYPE_WRAPPER_CLASS_TO_CONVERSION_METHOD_NAME.containsKey(paramTypeClassNode))) {
            initializePrimitiveOrTypeWrapperParameter(classNode, wrapper, param, requestParameterName);
        } else if (paramTypeClassNode.equals(new ClassNode(String.class))) {
            initializeStringParameter(classNode, wrapper, param, requestParameterName);
        } else if (!paramTypeClassNode.equals(OBJECT_CLASS)) {
            initializeAndValidateCommandObjectParameter(wrapper, classNode, paramTypeClassNode,
                    actionNode, actionName, paramName, source, context);
        }
    }

    protected void initializeAndValidateCommandObjectParameter(final BlockStatement wrapper,
            final ClassNode controllerNode, final ClassNode commandObjectNode,
            final ASTNode actionNode, final String actionName, final String paramName,
            final SourceUnit source, final GeneratorContext context) {
        final DeclarationExpression declareCoExpression = declX(localVarX(paramName, commandObjectNode), new EmptyExpression());
        wrapper.addStatement(stmt(declareCoExpression));

        if(commandObjectNode.isInterface() || Modifier.isAbstract(commandObjectNode.getModifiers())) {
            final String warningMessage = "The [" + actionName + "] action in [" +
                    controllerNode.getName() + "] accepts a parameter of type [" +
                    commandObjectNode.getName() +
                    "].  Interface types and abstract class types are not supported as command objects.  This parameter will be ignored.";
            GrailsASTUtils.warning(source, actionNode, warningMessage);
        } else {
            initializeCommandObjectParameter(wrapper, commandObjectNode, paramName, source);

            @SuppressWarnings("unchecked")
            boolean argumentIsValidateable = GrailsASTUtils.hasAnyAnnotations(
                    commandObjectNode,
                    grails.persistence.Entity.class,
                    jakarta.persistence.Entity.class) ||
                    commandObjectNode.implementsInterface(ClassHelper.make(Validateable.class));

            if (!argumentIsValidateable && commandObjectNode.isPrimaryClassNode()) {
                final ModuleNode commandObjectModule = commandObjectNode.getModule();
                if (commandObjectModule != null && this.compilationUnit != null) {
                    if (commandObjectModule == controllerNode.getModule() ||
                            doesModulePathIncludeSubstring(commandObjectModule,
                                    "grails-app" + File.separator + "controllers" + File.separator)) {

                        TraitInjectionUtils.injectTrait(compilationUnit, source, commandObjectNode, Validateable.class);

                        List<ConstructorNode> declaredConstructors = commandObjectNode.getDeclaredConstructors();
                        List<Statement> objectInitializerStatements = commandObjectNode.getObjectInitializerStatements();
                        if(declaredConstructors.isEmpty() && !objectInitializerStatements.isEmpty()) {
                            BlockStatement constructorLogic = new BlockStatement();
                            ConstructorNode constructorNode = new ConstructorNode(Modifier.PUBLIC, constructorLogic);
                            ClassNodeUtils.addGeneratedConstructor(commandObjectNode, constructorNode);
                            constructorLogic.addStatements(objectInitializerStatements);
                        }
                        argumentIsValidateable = true;
                    } else if (doesModulePathIncludeSubstring(commandObjectModule,
                            "grails-app" + File.separator + "domain" + File.separator)) {
                        argumentIsValidateable = true;
                    }
                }
            }
            
            if (argumentIsValidateable) {
                final MethodCallExpression validateMethodCallExpression = callX(localVarX(paramName, commandObjectNode), "validate");
                final MethodNode validateMethod = commandObjectNode.getMethod("validate", new Parameter[0]);
                if (validateMethod != null) {
                    validateMethodCallExpression.setMethodTarget(validateMethod);
                }
                final Statement ifCommandObjectIsNotNullThenValidate = ifS(boolX(varX(paramName)), stmt(validateMethodCallExpression));
                wrapper.addStatement(ifCommandObjectIsNotNullThenValidate);
            } else {
                // try to dynamically invoke the .validate() method if it is available at runtime...
                final Expression respondsToValidateMethodCallExpression = new MethodCallExpression(
                        new VariableExpression(paramName), "respondsTo", new ArgumentListExpression(
                                new ConstantExpression("validate")));
                final Expression validateMethodCallExpression = new MethodCallExpression(
                        new VariableExpression(paramName), "validate", new ArgumentListExpression());
                final Statement ifRespondsToValidateThenValidateStatement = new IfStatement(
                        new BooleanExpression(respondsToValidateMethodCallExpression),
                        new ExpressionStatement(validateMethodCallExpression),
                        new ExpressionStatement(new EmptyExpression()));
                final Statement ifCommandObjectIsNotNullThenValidate = new IfStatement(new BooleanExpression(new VariableExpression(paramName)), ifRespondsToValidateThenValidateStatement, new ExpressionStatement(new EmptyExpression()));
                wrapper.addStatement(ifCommandObjectIsNotNullThenValidate);
                
                final String warningMessage = "The [" + actionName + "] action accepts a parameter of type [" +
                        commandObjectNode.getName() +
                        "] which does not implement grails.validation.Validateable.  Data binding will still be applied " +
                        "to this command object but the instance will not be validateable.";
                GrailsASTUtils.warning(source, actionNode, warningMessage);
            }
            if(GrailsASTUtils.isInnerClassNode(commandObjectNode)) {
                final String warningMessage = "The [" + actionName + "] action accepts a parameter of type [" +
                        commandObjectNode.getName() +
                        "] which is an inner class. Command object classes should not be inner classes.";
                GrailsASTUtils.warning(source, actionNode, warningMessage);
                
            }
            else {
                new DefaultASTDatabindingHelper().injectDatabindingCode(source, context, commandObjectNode);
            }
        }
    }

    protected void initializeCommandObjectParameter(final BlockStatement wrapper,
            final ClassNode commandObjectNode, final String paramName, SourceUnit source) {
        final ArgumentListExpression initializeCommandObjectArguments = args(classX(commandObjectNode), constX(paramName));
        final MethodCallExpression initializeCommandObjectMethodCall = callThisX("initializeCommandObject", initializeCommandObjectArguments);
        applyDefaultMethodTarget(initializeCommandObjectMethodCall, commandObjectNode);
        final Expression assignCommandObjectToParameter = assignX(varX(paramName), initializeCommandObjectMethodCall);
        wrapper.addStatement(stmt(assignCommandObjectToParameter));
    }

    /**
     * Checks to see if a Module is defined at a path which includes the specified substring
     *
     * @param moduleNode a ModuleNode
     * @param substring The substring to search for
     * @return true if moduleNode is defined at a path which includes the specified substring
     */
    private boolean doesModulePathIncludeSubstring(ModuleNode moduleNode, final String substring) {

        if (moduleNode == null) {
            return false;
        }

        boolean substringFoundInDescription = false;
        String commandObjectModuleDescription = moduleNode.getDescription();
        if (commandObjectModuleDescription != null) {
            substringFoundInDescription = commandObjectModuleDescription.contains(substring);
        }
        return substringFoundInDescription;
    }

    protected void initializeStringParameter(final ClassNode classNode, final BlockStatement wrapper, final Parameter param,
            final String requestParameterName) {

        final ClassNode paramTypeClassNode = param.getType();
        final String methodParamName = param.getName();
        Expression getParamsExpression = buildGetPropertyExpression(new VariableExpression("this"), "params", classNode);
        final Expression paramsContainsKeyMethodArguments = new ArgumentListExpression(
                new ConstantExpression(requestParameterName));
        final BooleanExpression containsKeyExpression = new BooleanExpression(
                applyDefaultMethodTarget(new MethodCallExpression(getParamsExpression, "containsKey", paramsContainsKeyMethodArguments), Map.class));
        final Statement initializeParameterStatement = new ExpressionStatement(
                new DeclarationExpression(new VariableExpression(
                        methodParamName, paramTypeClassNode),
                        Token.newSymbol(Types.EQUALS, 0, 0),
                        new TernaryExpression(containsKeyExpression, buildGetMapExpression(getParamsExpression, requestParameterName) , new ConstantExpression(null))));
        wrapper.addStatement(initializeParameterStatement);
    }

    protected void initializePrimitiveOrTypeWrapperParameter(final ClassNode classNode, final BlockStatement wrapper,
            final Parameter param, final String requestParameterName) {

        final ClassNode paramTypeClassNode = param.getType();
        final String methodParamName = param.getName();
        final Expression defaultValueExpression;
        if (paramTypeClassNode.equals(ClassHelper.Boolean_TYPE)) {
            defaultValueExpression = new ConstantExpression(false);
        } else if (PRIMITIVE_CLASS_NODES.contains(paramTypeClassNode)) {
            defaultValueExpression = new ConstantExpression(0);
        } else {
            defaultValueExpression = new ConstantExpression(null);
        }

        final ConstantExpression paramConstantExpression = new ConstantExpression(requestParameterName);
        final Expression paramsTypeConversionMethodArguments = new ArgumentListExpression(
                paramConstantExpression/*, defaultValueExpression*/, new ConstantExpression(null));
        final String conversionMethodName;
        if (TYPE_WRAPPER_CLASS_TO_CONVERSION_METHOD_NAME.containsKey(paramTypeClassNode)) {
            conversionMethodName = TYPE_WRAPPER_CLASS_TO_CONVERSION_METHOD_NAME.get(paramTypeClassNode);
        } else {
            conversionMethodName = paramTypeClassNode.getName();
        }
        Expression getParamsExpression = buildGetPropertyExpression(new VariableExpression("this"), "params", classNode);
        final MethodCallExpression retrieveConvertedValueExpression = new MethodCallExpression(
                getParamsExpression, conversionMethodName, paramsTypeConversionMethodArguments);
        Class<?> defaultValueClass = null; // choose any
        if("char".equals(conversionMethodName)) {
            // TypeConvertingMap.'char' method has 2 different signatures, choose the one with "Character 'char'(String name, Integer defaultValue)" signature
            defaultValueClass = Integer.class;
        }
        applyMethodTarget(retrieveConvertedValueExpression, TypeConvertingMap.class, null, defaultValueClass);

        final Expression paramsContainsKeyMethodArguments = new ArgumentListExpression(paramConstantExpression);
        final BooleanExpression containsKeyExpression = new BooleanExpression(
                applyDefaultMethodTarget(new MethodCallExpression(getParamsExpression, "containsKey", paramsContainsKeyMethodArguments), Map.class));

        final Token equalsToken = Token.newSymbol(Types.EQUALS, 0, 0);
        final VariableExpression convertedValueExpression = new VariableExpression(
                "___converted_" + methodParamName, new ClassNode(Object.class));
        final DeclarationExpression declareConvertedValueExpression = new DeclarationExpression(
                convertedValueExpression, equalsToken, new EmptyExpression());

        Statement declareVariableStatement = new ExpressionStatement(declareConvertedValueExpression);
        wrapper.addStatement(declareVariableStatement);

        final VariableExpression methodParamExpression = new VariableExpression(
                methodParamName, paramTypeClassNode);
        final DeclarationExpression declareParameterVariableStatement = new DeclarationExpression(
                methodParamExpression, equalsToken, new EmptyExpression());
        declareVariableStatement = new ExpressionStatement(declareParameterVariableStatement);
        wrapper.addStatement(declareVariableStatement);

        final Expression assignmentExpression = new BinaryExpression(
                convertedValueExpression, equalsToken,
                new TernaryExpression(containsKeyExpression, retrieveConvertedValueExpression, defaultValueExpression));
        wrapper.addStatement(new ExpressionStatement(assignmentExpression));
        Expression rejectValueMethodCallExpression = getRejectValueExpression(classNode, methodParamName);

        BlockStatement ifConvertedValueIsNullBlockStatement = new BlockStatement();
        ifConvertedValueIsNullBlockStatement.addStatement(
                new ExpressionStatement(rejectValueMethodCallExpression));
        ifConvertedValueIsNullBlockStatement.addStatement(
                new ExpressionStatement(new BinaryExpression(
                        methodParamExpression, equalsToken, defaultValueExpression)));

        final BooleanExpression isConvertedValueNullExpression = new BooleanExpression(new BinaryExpression(
                convertedValueExpression, Token.newSymbol(Types.COMPARE_EQUAL, 0, 0),
                new ConstantExpression(null)));
        final ExpressionStatement assignConvertedValueToParamStatement = new ExpressionStatement(
                new BinaryExpression(methodParamExpression, equalsToken, convertedValueExpression));
        final Statement ifStatement = new IfStatement(isConvertedValueNullExpression,
                ifConvertedValueIsNullBlockStatement,
                assignConvertedValueToParamStatement);

        wrapper.addStatement(new IfStatement(new BooleanExpression(containsKeyExpression),
                ifStatement, new ExpressionStatement(new EmptyExpression())));
    }

    protected Expression getRejectValueExpression(final ClassNode classNode, final String methodParamName) {
        ArgumentListExpression rejectValueArgs = new ArgumentListExpression();
        rejectValueArgs.addExpression(new ConstantExpression(methodParamName));
        rejectValueArgs.addExpression(new ConstantExpression(
                "params." + methodParamName + ".conversion.error"));
        Expression getErrorsExpression = buildGetPropertyExpression(new VariableExpression("this", classNode), "errors", classNode);
        Expression rejectValueMethodCallExpression = applyDefaultMethodTarget(new MethodCallExpression(
                getErrorsExpression, "rejectValue", rejectValueArgs), Errors.class);
        return rejectValueMethodCallExpression;
    }

    public void performInjection(SourceUnit source, ClassNode classNode) {
        performInjection(source, null, classNode);
    }


    public boolean shouldInject(URL url) {
        return url != null && CONTROLLER_PATTERN.matcher(url.getFile()).find();
    }

    @Override
    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }
}
