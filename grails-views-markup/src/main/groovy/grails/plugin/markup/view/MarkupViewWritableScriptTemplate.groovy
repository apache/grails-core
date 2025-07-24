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

package grails.plugin.markup.view

import groovy.text.markup.MarkupTemplateEngine
import groovy.transform.CompileStatic

import grails.plugin.markup.view.api.MarkupView
import grails.views.GrailsViewTemplate
import grails.views.api.GrailsView

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class MarkupViewWritableScriptTemplate extends GrailsViewTemplate {

    MarkupTemplateEngine templateEngine
    MarkupViewConfiguration configuration

    MarkupViewWritableScriptTemplate(Class<? extends GrailsView> templateClass, File sourceFile, MarkupTemplateEngine templateEngine, MarkupViewConfiguration configuration) {
        super(templateClass, sourceFile)
        this.templateEngine = templateEngine
        this.configuration = configuration
    }

    @Override
    Writable make(Map binding) {
        MarkupView writableTemplate = (MarkupView)templateClass
                .newInstance(templateEngine, binding, Collections.emptyMap(), configuration)
        writableTemplate.viewTemplate = (GrailsViewTemplate)this
        writableTemplate.prettyPrint = prettyPrint

        writableTemplate.setSourceFile(sourceFile)

        return writableTemplate
    }
}
