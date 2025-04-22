/*
 * Copyright 2012 the original author or authors.
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
package org.grails.compiler.injection;

import grails.util.GrailsNameUtils;
import grails.util.Mixin;
import grails.util.MixinTargetAware;
import groovy.lang.GroovyObjectSupport;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * The logic for the {@link grails.util.Mixin} location transform.
 *
 * @author Graeme Rocher
 * @since 2.1.2
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class MixinTransformation implements ASTTransformation {

    public static final ClassNode GROOVY_OBJECT_CLASS_NODE = new ClassNode(GroovyObjectSupport.class);
    private static final ClassNode MY_TYPE = new ClassNode(Mixin.class);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    public static final String OBJECT_CLASS = "java.lang.Object";

    public void visit(ASTNode[] astNodes, SourceUnit source) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: $node.class / $parent.class");
        }

        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];
        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode classNode = (ClassNode) parent;
        String cName = classNode.getName();
        if (classNode.isInterface()) {
            throw new RuntimeException("Error processing interface '" + cName + "'. " +
                MY_TYPE_NAME + " not allowed for interfaces.");
        }

        ListExpression values = getListOfClasses(node);

        weaveMixinsIntoClass(classNode, values);

    }
    public void weaveMixinsIntoClass(ClassNode classNode, ListExpression values) {
        if (values != null) {
            for (Expression current : values.getExpressions()) {
                if (current instanceof ClassExpression) {
                    ClassExpression ce = (ClassExpression) current;

                    ClassNode mixinClassNode = ce.getType();

                    final String fieldName = '$' + GrailsNameUtils.getPropertyName(mixinClassNode.getName());

                    if (classNode != null && classNode.getField(fieldName) == null) {
                        boolean isTargetAware = GrailsASTUtils.findInterface(mixinClassNode, new ClassNode(MixinTargetAware.class)) != null;

                        ConstructorCallExpression initialValue;
                        if(isTargetAware) {
                            initialValue = new ConstructorCallExpression(mixinClassNode, new MapExpression(
                                    Arrays.asList(new MapEntryExpression(new ConstantExpression("target"), new VariableExpression("this")))
                            ));
                        }  else {
                            initialValue = new ConstructorCallExpression(mixinClassNode, GrailsASTUtils.ZERO_ARGUMENTS);
                        }
                        classNode.addField(fieldName, Modifier.PRIVATE, mixinClassNode,initialValue);
                    }

                    VariableExpression fieldReference = new VariableExpression(fieldName, mixinClassNode);

                    while (!mixinClassNode.getName().equals(OBJECT_CLASS)) {
                        final List<MethodNode> mixinMethods = mixinClassNode.getMethods();

                        for (MethodNode mixinMethod : mixinMethods) {
                            if (isCandidateMethod(mixinMethod) && !hasDeclaredMethod(classNode, mixinMethod)) {
                                if (mixinMethod.isStatic()) {
                                    GrailsASTUtils.addCompileStaticAnnotation(GrailsASTUtils.addDelegateStaticMethod(classNode, mixinMethod));
                                }
                                else {
                                    GrailsASTUtils.addCompileStaticAnnotation(GrailsASTUtils.addDelegateInstanceMethod(classNode, fieldReference, mixinMethod, false));
                                }
                            }
                        }

                        mixinClassNode = mixinClassNode.getSuperClass();
                    }
                }
            }
        }
    }

    protected boolean hasDeclaredMethod(ClassNode classNode, MethodNode mixinMethod) {
        return classNode.hasDeclaredMethod(mixinMethod.getName(), mixinMethod.getParameters());
    }
    protected ListExpression getListOfClasses(AnnotationNode node) {
        Expression value = node.getMember("value");
        ListExpression values = null;
        if (value instanceof ListExpression) {
            values = (ListExpression) value;
        } else if (value instanceof ClassExpression) {
            values = new ListExpression();
            values.addExpression(value);
        }

        return values;
    }

    protected boolean isCandidateMethod(MethodNode declaredMethod) {
        return isAddableMethod(declaredMethod);
    }

    public static boolean isAddableMethod(MethodNode declaredMethod) {
        ClassNode groovyMethods = GROOVY_OBJECT_CLASS_NODE;
        String methodName = declaredMethod.getName();
        return !declaredMethod.isSynthetic() &&
            !methodName.contains("$") &&
            Modifier.isPublic(declaredMethod.getModifiers()) &&
            !Modifier.isAbstract(declaredMethod.getModifiers()) &&
            !groovyMethods.hasMethod(declaredMethod.getName(), declaredMethod.getParameters());
    }
}
