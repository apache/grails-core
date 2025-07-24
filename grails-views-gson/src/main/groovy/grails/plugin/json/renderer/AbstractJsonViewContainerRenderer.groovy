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

import org.springframework.beans.factory.annotation.Autowired

import grails.plugin.json.view.mvc.JsonViewResolver
import grails.rest.render.RenderContext
import grails.util.GrailsNameUtils
import grails.views.Views
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.json.DefaultJsonRenderer

/**
 * A container renderer that looks up JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@InheritConstructors
abstract class AbstractJsonViewContainerRenderer<C,T> extends DefaultJsonRenderer<T> {

    @Autowired
    JsonViewResolver jsonViewResolver

    @Override
    void render(T object, RenderContext context) {
        if(jsonViewResolver != null) {
            String viewUri = "/${context.controllerName}/_${GrailsNameUtils.getPropertyName(targetType)}"
            def webRequest = ((ServletRenderContext) context).getWebRequest()
            if (webRequest.controllerNamespace) {
                viewUri = "/${webRequest.controllerNamespace}" + viewUri
            }
            def view = jsonViewResolver.resolveView(viewUri, context.locale)
            if(view == null) {
                view = jsonViewResolver.resolveView(targetType, context.locale)
            }

            if(view != null) {
                Map<String, Object> model = (Map<String, Object>) [(resolveModelName()): object]
                def contextArguments = context.getArguments()
                def contextModel = contextArguments?.get(Views.MODEL)
                if(contextModel instanceof Map) {
                    model.putAll((Map)contextModel)
                }

                def request = webRequest.currentRequest
                def response = webRequest.currentResponse
                view.render(model, request, response)
            }
            else {
                super.render(object, context)
            }
        }
        else {
            super.render(object, context)
        }
    }

    protected String resolveModelName() {
        GrailsNameUtils.getPropertyName(targetType)
    }
}
