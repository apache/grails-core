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

package functional.tests

/**
 * Actions that render a template with {@code render(template: ..., model: ...)}, so that no
 * {@code ModelAndView} is produced and interceptors read the model passed to the template.
 */
class RenderTemplateController {

    def index() {
        render(template: 'snippet', model: [title: 'x'])
    }

    def bean() {
        render(template: 'snippet', bean: 'b', model: [title: 'x'])
    }

    /**
     * Renders a template (setting TEMPLATE_MODEL) then forwards to {@code forwardTarget}.
     * The interceptor on {@code forwardTarget} must not see the TEMPLATE_MODEL from this action.
     */
    def forwardAfterTemplate() {
        render(template: 'snippet', model: [title: 'leaked'])
        forward(action: 'forwardTarget')
    }

    /**
     * Target of the forward from {@code forwardAfterTemplate}. Has no model of its own.
     */
    def forwardTarget() {
        render text: 'ok', contentType: 'text/plain'
    }

    /**
     * Renders a template (setting TEMPLATE_MODEL) then uses g:include to include {@code includeTarget}.
     * The interceptor on {@code includeTarget} must not see the TEMPLATE_MODEL from this action.
     */
    def includeAfterTemplate() {
        render(template: 'snippet', model: [title: 'leaked'])
        render text: g.include(controller: 'renderTemplate', action: 'includeTarget'), contentType: 'text/plain'
    }

    /**
     * Target of the include from {@code includeAfterTemplate}. Has no model of its own.
     */
    def includeTarget() {
        render text: 'included', contentType: 'text/plain'
    }
}
