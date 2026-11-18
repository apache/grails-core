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

package grails.plugin.json.renderer

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import org.springframework.validation.Errors
import org.springframework.web.servlet.View

/**
 * Renderer for the errors view
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@CompileStatic
class ErrorsJsonViewRenderer extends AbstractJsonViewContainerRenderer<Errors, Object> {

    final Class<Object> componentType = Object

    /**
     * Validation errors are rendered by an errors template, the controller's or {@code /errors/_errors.gson}, and otherwise
     * as the JSON renderer writes them, with a {@code 422}: the object template renders the value it is given as
     * {@code object}, which validation errors are not given as.
     *
     * @since 9.0
     */
    @Override
    protected View resolveTypeView(Locale locale) {
        View view = super.resolveTypeView(locale)
        view.is(jsonViewResolver.objectView) ? null : view
    }
}
