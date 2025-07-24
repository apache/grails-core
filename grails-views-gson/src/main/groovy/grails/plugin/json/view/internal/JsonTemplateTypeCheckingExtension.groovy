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

package grails.plugin.json.view.internal

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.SourceUnit

import grails.plugin.json.builder.StreamingJsonBuilder
import grails.plugin.json.view.api.internal.TemplateRenderer
import grails.views.api.http.Parameters
import grails.views.compiler.BuilderTypeCheckingExtension

/**
 * A type checking extension for JSON builder
 *
 * @author Graeme Rocher
 */
@CompileStatic
class JsonTemplateTypeCheckingExtension extends BuilderTypeCheckingExtension {
    private static final ClassNode BUILDER_CLASS_NODE = ClassHelper.make(StreamingJsonBuilder)
    private static final MethodNode JSON_BUILDER_INVOKE_METHOD = ClassHelper.make(StreamingJsonBuilder).getMethods('invokeMethod')[0]
    private static final MethodNode JSON_DELEGATE_INVOKE_METHOD = ClassHelper.make(StreamingJsonBuilder.StreamingJsonDelegate).getMethods('invokeMethod')[0]
    private static final ClassNode TEMPLATE_NAMESPACE = ClassHelper.make(TemplateRenderer)
    private static final ClassNode PARAMETERS = ClassHelper.make(Parameters)
    private static final MethodNode TEMPLATE_NAMESPACE_INVOKE_METHOD = ClassHelper.make(TemplateRenderer).getMethods('invokeMethod')[0]

    JsonTemplateTypeCheckingExtension() {
        insideScope = false
    }

    @Override
    void beforeMethodCallExpression(MethodCallExpression methodCallExpression) {
        if(!insideScope) {
            if(methodCallExpression.methodAsString == 'json') {
                insideScope = true
            }
            else if(methodCallExpression.objectExpression instanceof VariableExpression) {
                VariableExpression ve = methodCallExpression.objectExpression as VariableExpression
                if(ve.name == 'json') {
                    insideScope = true
                }
            }
        }
    }

    @Override
    @CompileDynamic
    boolean isMethodDynamic(Object receiver, Object name, Object argList, Object argTypes, Object call) {
        if( receiver.name == TEMPLATE_NAMESPACE.name) {
            return true
        }
        return super.isMethodDynamic(receiver, name, argList, argTypes, call)
    }

    @Override
    boolean isPropertyDynamic(PropertyExpression propertyExpression) {
        def oe = propertyExpression.getObjectExpression()
        if(oe instanceof VariableExpression) {
            return "params".equals(((VariableExpression)oe).name)
        }
        return super.isPropertyDynamic(propertyExpression)
    }

    @Override
    void transformDynamicMethods(SourceUnit source, MethodNode mn, Set dynamicCalls) {
        new BuilderTypeCheckingExtension.BuilderMethodReplacer(TEMPLATE_NAMESPACE_INVOKE_METHOD, TEMPLATE_NAMESPACE_INVOKE_METHOD, ":IGNORE", source, dynamicCalls)
                .visitMethod(mn)
    }

    @Override
    MethodNode getBuilderInvokeMethod() {
        JSON_BUILDER_INVOKE_METHOD
    }

    @Override
    MethodNode getDelegateInvokeMethod() {
        JSON_DELEGATE_INVOKE_METHOD
    }

    @Override
    String getBuilderVariableName() {
        "json"
    }

    @Override
    ClassNode getBuilderClassNode() {
        return BUILDER_CLASS_NODE
    }
}
