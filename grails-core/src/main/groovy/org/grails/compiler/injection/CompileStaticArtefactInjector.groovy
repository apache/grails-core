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
package org.grails.compiler.injection

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit

import grails.artefact.Artefact
import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.ServiceArtefactHandler

/**
 * Opts controllers, services and tag libraries into {@code @GrailsCompileStatic} automatically when the
 * corresponding build opt-in is enabled.
 *
 * <p>The opt-ins are surfaced as system properties by the Grails Gradle plugin (see
 * {@code grails { compileStaticControllers = true }}, {@code grails { compileStaticServices = true }} and
 * {@code grails { compileStaticTagLibs = true }}). When enabled, every matching artefact is compiled
 * statically unless it already declares its own {@code @CompileStatic}, {@code @CompileDynamic},
 * {@code @TypeChecked}, {@code @GrailsCompileStatic} or {@code @GrailsTypeChecked} annotation - an
 * explicit choice on the class always wins.</p>
 *
 * @author Grails
 * @since 8.0
 */
@AstTransformer
@CompileStatic
class CompileStaticArtefactInjector implements GrailsArtefactClassInjector {

    // Keep in sync with the matching constants in grails.util.BuildSettings. Referenced as literals
    // because the AST transform runs in the compiler worker JVM, which reads the values published by
    // the Grails Gradle plugin as system properties.
    static final String COMPILE_STATIC_CONTROLLERS_PROPERTY = 'grails.compile.artefacts.controllers.static'
    static final String COMPILE_STATIC_SERVICES_PROPERTY = 'grails.compile.artefacts.services.static'
    static final String COMPILE_STATIC_TAGLIBS_PROPERTY = 'grails.compile.artefacts.taglibs.static'

    // grails-core has no dependency on the taglib module; matches
    // org.grails.core.artefact.gsp.TagLibArtefactHandler.TYPE.
    static final String TAGLIB_TYPE = 'TagLib'

    private static final ClassNode ARTEFACT_CLASS_NODE = ClassHelper.make(Artefact)

    @Override
    String[] getArtefactTypes() {
        [ControllerArtefactHandler.TYPE, ServiceArtefactHandler.TYPE, TAGLIB_TYPE] as String[]
    }

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        maybeApplyCompileStatic(classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        maybeApplyCompileStatic(classNode)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        maybeApplyCompileStatic(classNode)
    }

    @Override
    boolean shouldInject(URL url) {
        true
    }

    private static void maybeApplyCompileStatic(ClassNode classNode) {
        String artefactType = resolveArtefactType(classNode)
        if (artefactType == null) {
            return
        }
        if (artefactType == ControllerArtefactHandler.TYPE && Boolean.getBoolean(COMPILE_STATIC_CONTROLLERS_PROPERTY)) {
            GrailsASTUtils.addGrailsCompileStaticAnnotation(classNode)
        }
        else if (artefactType == ServiceArtefactHandler.TYPE && Boolean.getBoolean(COMPILE_STATIC_SERVICES_PROPERTY)) {
            GrailsASTUtils.addGrailsCompileStaticAnnotation(classNode)
        }
        else if (artefactType == TAGLIB_TYPE && Boolean.getBoolean(COMPILE_STATIC_TAGLIBS_PROPERTY)) {
            GrailsASTUtils.addGrailsCompileStaticAnnotation(classNode)
        }
    }

    private static String resolveArtefactType(ClassNode classNode) {
        List<AnnotationNode> annotations = classNode.getAnnotations(ARTEFACT_CLASS_NODE)
        if (!annotations) {
            return null
        }
        Expression value = annotations.get(0).getMember('value')
        if (value instanceof ConstantExpression) {
            Object constant = ((ConstantExpression) value).getValue()
            return constant == null ? null : constant.toString()
        }
        null
    }
}
