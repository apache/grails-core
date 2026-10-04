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
package org.grails.compiler.web.taglib

import java.lang.reflect.Modifier
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit

import grails.artefact.TagLibrary
import grails.compiler.ast.AnnotatedClassInjector
import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.io.support.GrailsResourceUtils
import org.grails.taglib.TagOutput
import org.grails.taglib.encoder.OutputContextLookupHelper

/**
 * Enhances tag library classes with the appropriate API at compile time.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
@AstTransformer
class TagLibraryTransformer implements GrailsArtefactClassInjector, AnnotatedClassInjector {

    protected static final String GET_TAG_LIB_NAMESPACE_METHOD_NAME = '$getTagLibNamespace'

    static Pattern TAGLIB_PATTERN = Pattern.compile('.+/' +
            GrailsResourceUtils.GRAILS_APP_DIR + '/taglib/(.+)TagLib\\.groovy')

    private static final String ATTRS_ARGUMENT = 'attrs'
    private static final String BODY_ARGUMENT = 'body'
    private static final Parameter[] MAP_CLOSURE_PARAMETERS = [
        new Parameter(new ClassNode(Map), ATTRS_ARGUMENT),
        new Parameter(new ClassNode(Closure), BODY_ARGUMENT) ] as Parameter[]
    private static final Parameter[] CLOSURE_PARAMETERS = [
        new Parameter(new ClassNode(Closure), BODY_ARGUMENT) ] as Parameter[]
    private static final Parameter[] MAP_PARAMETERS = [
        new Parameter(new ClassNode(Map), ATTRS_ARGUMENT) ] as Parameter[]
    private static final Parameter[] MAP_CHARSEQUENCE_PARAMETERS = [
        new Parameter(new ClassNode(Map), ATTRS_ARGUMENT),
        new Parameter(new ClassNode(CharSequence), BODY_ARGUMENT) ] as Parameter[]
    private static final ClassNode TAG_OUTPUT_CLASS_NODE = new ClassNode(TagOutput)
    private static final VariableExpression ATTRS_EXPRESSION = new VariableExpression(ATTRS_ARGUMENT)
    private static final VariableExpression BODY_EXPRESSION = new VariableExpression(BODY_ARGUMENT)
    private static final MethodCallExpression CURRENT_OUTPUT_CONTEXT_METHOD_CALL =
        new MethodCallExpression(new ClassExpression(new ClassNode(OutputContextLookupHelper)),
                'lookupOutputContext', ZERO_ARGS)
    private static final Expression NULL_EXPRESSION = new ConstantExpression(null)
    private static final String NAMESPACE_PROPERTY = 'namespace'
    private static final ClassNode CLOSURE_CLASS_NODE = new ClassNode(Closure)

    @Override
    String[] getArtefactTypes() {
        return [ getArtefactType(), 'TagLibrary' ] as String[]
    }

    protected String getArtefactType() {
        return TagLibArtefactHandler.TYPE
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        List<PropertyNode> tags = findTags(classNode)

        PropertyNode namespaceProperty = classNode.getProperty(NAMESPACE_PROPERTY)
        String namespace = TagOutput.DEFAULT_NAMESPACE
        if (namespaceProperty != null && namespaceProperty.isStatic()) {
            Expression initialExpression = namespaceProperty.getInitialExpression()
            if (initialExpression instanceof ConstantExpression) {
                namespace = initialExpression.getText()
            }
        }

        addGetTagLibNamespaceMethod(classNode, namespace)

        MethodCallExpression tagLibraryLookupMethodCall = new MethodCallExpression(new VariableExpression('this', ClassHelper.make(TagLibrary)), 'getTagLibraryLookup', ZERO_ARGS)
        for (PropertyNode tag in tags) {
            String tagName = tag.getName()
            addAttributesAndBodyMethod(classNode, tagLibraryLookupMethodCall, tagName)
            addAttributesAndStringBodyMethod(classNode, tagName)
            addAttributesAndBodyMethod(classNode, tagLibraryLookupMethodCall, tagName, false)
            addAttributesAndBodyMethod(classNode, tagLibraryLookupMethodCall, tagName, true, false)
            addAttributesAndBodyMethod(classNode, tagLibraryLookupMethodCall, tagName, false, false)
        }
    }

    private void addGetTagLibNamespaceMethod(final ClassNode classNode, final String namespace) {
        final ConstantExpression namespaceConstantExpression = new ConstantExpression(namespace)
        Statement returnNamespaceStatement = new ReturnStatement(namespaceConstantExpression)
        final MethodNode m = new MethodNode(GET_TAG_LIB_NAMESPACE_METHOD_NAME, Modifier.PROTECTED, new ClassNode(String), Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, returnNamespaceStatement)
        classNode.addMethod(m)
    }

    private void addAttributesAndStringBodyMethod(ClassNode classNode, String tagName) {
        BlockStatement methodBody = new BlockStatement()
        ArgumentListExpression arguments = new ArgumentListExpression()
        ArgumentListExpression constructorArgs = new ArgumentListExpression()
        constructorArgs.addExpression(BODY_EXPRESSION)
        arguments.addExpression(new CastExpression(ClassHelper.make(Map), ATTRS_EXPRESSION))
                 .addExpression(new ConstructorCallExpression(new ClassNode(TagOutput.ConstantClosure), constructorArgs))
        methodBody.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression('this'), tagName, arguments)))
        classNode.addMethod(new MethodNode(tagName, Modifier.PUBLIC, GrailsASTUtils.OBJECT_CLASS_NODE, MAP_CHARSEQUENCE_PARAMETERS, ClassNode.EMPTY_ARRAY, methodBody))
    }

    private void addAttributesAndBodyMethod(ClassNode classNode, MethodCallExpression tagLibraryLookupMethodCall, String tagName) {
        addAttributesAndBodyMethod(classNode, tagLibraryLookupMethodCall, tagName, true)
    }

    private void addAttributesAndBodyMethod(ClassNode classNode, MethodCallExpression tagLibraryLookupMethodCall, String tagName, boolean includeBody) {
        addAttributesAndBodyMethod(classNode, tagLibraryLookupMethodCall, tagName, includeBody, true)
    }

    private void addAttributesAndBodyMethod(ClassNode classNode, MethodCallExpression tagLibraryLookupMethodCall, String tagName, boolean includeBody, boolean includeAttrs) {
        BlockStatement methodBody = new BlockStatement()
        ArgumentListExpression arguments = new ArgumentListExpression()
        arguments.addExpression(tagLibraryLookupMethodCall)
                 .addExpression(new MethodCallExpression(new VariableExpression('this'), GET_TAG_LIB_NAMESPACE_METHOD_NAME, new ArgumentListExpression()))
                 .addExpression(new ConstantExpression(tagName))
                 .addExpression(includeAttrs ? new CastExpression(ClassHelper.make(Map), ATTRS_EXPRESSION) : new MapExpression())
                 .addExpression(includeBody ? BODY_EXPRESSION : NULL_EXPRESSION)
                 .addExpression(CURRENT_OUTPUT_CONTEXT_METHOD_CALL)

        methodBody.addStatement(new ExpressionStatement(new MethodCallExpression(new ClassExpression(TAG_OUTPUT_CLASS_NODE), 'captureTagOutput', arguments)))

        if (includeBody && includeAttrs) {
            if (!methodExists(classNode, tagName, MAP_CLOSURE_PARAMETERS)) {
                classNode.addMethod(new MethodNode(tagName, Modifier.PUBLIC, GrailsASTUtils.OBJECT_CLASS_NODE, MAP_CLOSURE_PARAMETERS, ClassNode.EMPTY_ARRAY, methodBody))
            }
        }
        else if (includeAttrs) {
            if (!methodExists(classNode, tagName, MAP_PARAMETERS)) {
                classNode.addMethod(new MethodNode(tagName, Modifier.PUBLIC, GrailsASTUtils.OBJECT_CLASS_NODE, MAP_PARAMETERS, ClassNode.EMPTY_ARRAY, methodBody))
            }
        }
        else if (includeBody) {
            if (!methodExists(classNode, tagName, CLOSURE_PARAMETERS)) {
                classNode.addMethod(new MethodNode(tagName, Modifier.PUBLIC, GrailsASTUtils.OBJECT_CLASS_NODE, CLOSURE_PARAMETERS, ClassNode.EMPTY_ARRAY, methodBody))
            }
        }
        else {
            if (!methodExists(classNode, tagName, Parameter.EMPTY_ARRAY)) {
                classNode.addMethod(new MethodNode(tagName, Modifier.PUBLIC, GrailsASTUtils.OBJECT_CLASS_NODE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, methodBody))
            }
        }
    }

    private boolean methodExists(ClassNode classNode, String methodName, Parameter[] parameters) {
        return classNode.getMethod(methodName, parameters) != null
    }

    private List<PropertyNode> findTags(ClassNode classNode) {
        List<PropertyNode> tags = new ArrayList<>()
        List<PropertyNode> properties = classNode.getProperties()
        List<PropertyNode> potentialAliases = new ArrayList<>()
        for (PropertyNode property in properties) {
            if (property.isPublic()) {
                Expression initialExpression = property.getInitialExpression()
                if (initialExpression instanceof ClosureExpression) {
                    ClosureExpression ce = (ClosureExpression) initialExpression
                    Parameter[] parameters = ce.getParameters()

                    if (parameters.length <= 2) {
                        tags.add(property)
                        //force Closure type for DefaultGrailsTagLibClass
                        property.setType(CLOSURE_CLASS_NODE)
                    }
                }
                else if (initialExpression instanceof VariableExpression) {
                    potentialAliases.add(property)
                }
            }
        }

        for (PropertyNode potentialAlias in potentialAliases) {
            VariableExpression pe = (VariableExpression) potentialAlias.getInitialExpression()

            String propertyName = pe.getName()
            PropertyNode property = classNode.getProperty(propertyName)
            if (property != null && tags.contains(property)) {
                potentialAlias.setType(CLOSURE_CLASS_NODE)
                tags.add(potentialAlias)
            }
        }
        return tags
    }

    boolean shouldInject(URL url) {
        return url != null && TAGLIB_PATTERN.matcher(url.getFile()).find()
    }
}
