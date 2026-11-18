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
package org.grails.gradle.plugin.views.json

import org.grails.gradle.plugin.views.AbstractViewCompileEnvironmentFunctionalSpec

/**
 * Covers the environment {@link GsonViewCompilerTask} compiles JSON views for: the view compiler
 * reads the application's configuration for one environment too, from the same kind of forked task
 * as the page compiler.
 *
 * @since 7.0.18
 */
class GsonViewCompileEnvironmentFunctionalSpec extends AbstractViewCompileEnvironmentFunctionalSpec {

    @Override
    String getProjectName() {
        'gson-compile-environment'
    }

    @Override
    String getTaskName() {
        'compileGsonViews'
    }

    @Override
    String getDestination() {
        'build/gson-classes/main'
    }

    // the view compiler comes from grails-views-gson, which this build cannot depend on
    @Override
    String getStandIn() {
        'grails/plugin/json/view/JsonViewCompiler.class'
    }

    @Override
    File view(File checkout, String name) {
        File view = new File(checkout, "grails-app/views/book/${name}.gson")
        view.parentFile.mkdirs()
        view.text = 'json { title book.title }'
        view
    }
}
