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

import org.grails.gradle.plugin.views.AbstractViewCompileEnvironmentFunctionalSpec

/**
 * Covers the environment {@link MarkupViewCompilerTask} compiles markup views for, as the specification of
 * the JSON views does for theirs. It is also what shows that the plugin of the markup views can be applied
 * to a build at all: the task once declared the properties of its parent a second time and left them
 * unset, and the plugin failed on the first of them it read.
 *
 * @since 9.0
 */
class MarkupViewCompileEnvironmentFunctionalSpec extends AbstractViewCompileEnvironmentFunctionalSpec {

    @Override
    String getProjectName() {
        'markup-compile-environment'
    }

    @Override
    String getTaskName() {
        'compileGmlViews'
    }

    @Override
    String getDestination() {
        'build/gml-classes/main'
    }

    // the view compiler comes from grails-views-markup, which this build cannot depend on
    @Override
    String getStandIn() {
        'grails/plugin/markup/view/MarkupViewCompiler.class'
    }

    @Override
    File view(File checkout, String name) {
        File view = new File(checkout, "grails-app/views/book/${name}.gml")
        view.parentFile.mkdirs()
        view.text = 'book { title(book.title) }'
        view
    }
}
