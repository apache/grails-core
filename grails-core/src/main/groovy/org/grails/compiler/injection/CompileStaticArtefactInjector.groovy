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
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClosureExpression
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
 * <p>The opt-ins are surfaced as system properties by the Grails Gradle plugin (see the nested
 * {@code grails { compileStatic { controllers = true; services = true; tagLibs = true } }} block).
 * When enabled, every matching artefact is compiled
 * statically unless it already declares its own {@code @CompileStatic}, {@code @CompileDynamic},
 * {@code @TypeChecked}, {@code @GrailsCompileStatic} or {@code @GrailsTypeChecked} annotation - an
 * explicit choice on the class always wins.</p>
 *
 * <p>The tag library opt-in additionally skips tag libraries that still define tags the deprecated way -
 * as closure fields rather than methods - because such tags cannot be compiled statically when they
 * dispatch to other tags. These are left dynamically compiled and a warning is emitted; migrate the tags
 * to methods (e.g. {@code def myTag(Map attrs, Closure body)}) to opt them in, or annotate the class with
 * {@code @GrailsCompileStatic} to force static compilation regardless.</p>
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
        maybeApplyCompileStatic(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        maybeApplyCompileStatic(source, classNode)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        maybeApplyCompileStatic(source, classNode)
    }

    @Override
    boolean shouldInject(URL url) {
        true
    }

    private static void maybeApplyCompileStatic(SourceUnit source, ClassNode classNode) {
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
            if (hasClosureFieldTag(classNode)) {
                GrailsASTUtils.warning(source, classNode, legacyTagLibWarning(classNode))
                return
            }
            GrailsASTUtils.addGrailsCompileStaticAnnotation(classNode)
        }
    }

    /**
     * Detects a tag library that still declares tags the deprecated way - as closure fields (a public,
     * non-static closure-valued property or field) rather than methods. This matches how Grails identifies
     * tags at runtime, and such closure-field tags cannot be compiled statically when they dispatch to
     * other tags, so the tag library opt-in skips them.
     */
    private static boolean hasClosureFieldTag(ClassNode classNode) {
        for (PropertyNode property : classNode.getProperties()) {
            if (!property.isStatic() && property.getInitialExpression() instanceof ClosureExpression) {
                return true
            }
        }
        for (FieldNode field : classNode.getFields()) {
            if (field.isPublic() && !field.isStatic() && field.getInitialExpression() instanceof ClosureExpression) {
                return true
            }
        }
        false
    }

    private static String legacyTagLibWarning(ClassNode classNode) {
        'Tag library ' + classNode.getName() + ' defines one or more tags as closure fields (the deprecated form), ' +
                'so it was skipped by the grails { compileStatic { tagLibs = true } } opt-in and left dynamically compiled. ' +
                'Migrate its tags to methods (e.g. def myTag(Map attrs, Closure body)) to compile it statically, ' +
                'or annotate the class with @GrailsCompileStatic to force static compilation.'
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
