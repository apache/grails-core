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
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

import com.sun.source.util.JavacTask
import com.sun.source.util.TaskEvent
import com.sun.source.util.TaskListener
import groovy.transform.CompileStatic

import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Explicit javac processor for Java-only plugin modules. Reconciles the class
 * directory once javac has finished generating all classes in the compilation.
 */
@CompileStatic
@SupportedAnnotationTypes('*')
@SupportedOptions('grails.plugin.version')
class JavaPluginDescriptorProcessor extends AbstractProcessor {

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
        false
    }

    private static void writeDescriptors(ProcessingEnvironment environment) {
        def index = environment.filer.createResource(StandardLocation.CLASS_OUTPUT, '', PluginDescriptorWriter.PLUGINS_IMPORTS_PATH)
        File directory = new File(index.toUri()).parentFile.parentFile.parentFile
        CompilerPluginDescriptors.reconcile(directory, environment.options.get('grails.plugin.version'), true)
    }
}
