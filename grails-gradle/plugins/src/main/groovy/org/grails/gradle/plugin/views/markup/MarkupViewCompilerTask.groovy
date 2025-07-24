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

package org.grails.gradle.plugin.views.markup

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.process.ExecOperations

import org.grails.gradle.plugin.views.AbstractGroovyTemplateCompileTask

/**
 * MarkupView compiler task for Gradle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@CacheableTask
class MarkupViewCompilerTask extends AbstractGroovyTemplateCompileTask {

    @Input
    final Property<String> fileExtension

    @Input
    final Property<String> scriptBaseName

    @Input
    final Property<String> compilerName

    @Inject
    MarkupViewCompilerTask(ExecOperations execOperations, ObjectFactory objectFactory) {
        super(execOperations, objectFactory, 'gml', 'grails.plugin.markup.view.MarkupViewTemplate', 'grails.plugin.markup.view.MarkupViewCompiler')
    }
}
