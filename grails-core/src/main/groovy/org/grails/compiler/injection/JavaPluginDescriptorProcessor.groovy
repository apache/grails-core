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

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation

import com.sun.source.util.JavacTask
import com.sun.source.util.TaskEvent
import com.sun.source.util.TaskListener
import groovy.transform.CompileStatic

import grails.plugins.metadata.GrailsPluginDescriptor
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Writes the descriptors of the plugins a javac compilation produces, beside its classes, once javac has
 * written them all, as {@link PluginDescriptorTransformation} does for Groovy. An annotated class that is
 * not a concrete {@code grails.plugins.Plugin} subclass is reported as an error, as is a plugin that
 * declares {@code doWithSpring}, a hook plugins no longer have. The plugin version is
 * the {@code grails.plugin.version} processor option.
 */
@CompileStatic
@SupportedAnnotationTypes('grails.plugins.metadata.GrailsPluginDescriptor')
@SupportedOptions('grails.plugin.version')
class JavaPluginDescriptorProcessor extends AbstractProcessor {

    private static final String PLUGIN_CLASS = 'grails.plugins.Plugin'

    @Override
    SourceVersion getSupportedSourceVersion() {
        SourceVersion.latestSupported()
    }

    @Override
    synchronized void init(ProcessingEnvironment environment) {
        super.init(environment)
        JavacTask.instance(environment).addTaskListener(new TaskListener() {
            @Override
            void finished(TaskEvent event) {
                if (event.kind == TaskEvent.Kind.COMPILATION) {
                    writeDescriptors(environment)
                }
            }
        })
    }

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement plugin = processingEnv.elementUtils.getTypeElement(PLUGIN_CLASS)
        TypeMirror pluginType = plugin != null ? processingEnv.typeUtils.erasure(plugin.asType()) : null
        for (Element element : round.getElementsAnnotatedWith(GrailsPluginDescriptor)) {
            boolean concrete = element.kind == ElementKind.CLASS && !element.modifiers.contains(Modifier.ABSTRACT)
            if (!concrete || pluginType == null || !processingEnv.typeUtils.isAssignable(element.asType(), pluginType)) {
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                        "${element} is annotated with @GrailsPluginDescriptor, so it must be a concrete subclass of ${PLUGIN_CLASS}".toString(),
                        element)
                continue
            }
            for (TypeElement declaring = (TypeElement) element; declaring != null && !declaring.qualifiedName.contentEquals(PLUGIN_CLASS);
                    declaring = superclassOf(declaring)) {
                for (Element member : declaring.enclosedElements) {
                    if (member.kind == ElementKind.METHOD &&
                            member.simpleName.contentEquals(PluginDescriptorTransformation.REMOVED_BEAN_HOOK)) {
                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                                PluginDescriptorTransformation.removedBeanHookMessage(element.toString()),
                                declaring.is(element) ? member : element)
                    }
                }
            }
        }
        false
    }

    private TypeElement superclassOf(TypeElement type) {
        TypeMirror superclass = type.superclass
        superclass instanceof DeclaredType ? (TypeElement) ((DeclaredType) superclass).asElement() : null
    }

    private static void writeDescriptors(ProcessingEnvironment environment) {
        def index = environment.filer.createResource(StandardLocation.CLASS_OUTPUT, '', PluginDescriptorWriter.PLUGINS_IMPORTS_PATH)
        File directory = new File(index.toUri()).parentFile.parentFile.parentFile
        CompilerPluginDescriptors.reconcile(directory, environment.options.get('grails.plugin.version'))
    }
}
