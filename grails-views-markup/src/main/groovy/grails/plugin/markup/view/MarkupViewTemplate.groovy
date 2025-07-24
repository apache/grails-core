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

import groovy.text.markup.BaseTemplate
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration

import grails.plugin.markup.view.api.MarkupView
import grails.views.WritableScript

/**
 * Base class for markup engine templates
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class MarkupViewTemplate extends BaseTemplate implements WritableScript, MarkupView {

    public static final String EXTENSION = "gml"
    public static final String TYPE = "views.gml"

    File sourceFile

    MarkupViewTemplate(MarkupTemplateEngine templateEngine, Map model, Map<String, String> modelTypes, TemplateConfiguration configuration) {
        super(templateEngine, model, modelTypes, configuration)
    }

    @Override
    void setBinding(Binding binding) {
        ((Script)this).setBinding(binding)
    }

    @Override
    Binding getBinding() {
        return ((Script)this).getBinding()
    }
}
