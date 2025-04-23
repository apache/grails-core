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

package grails.views.api.internal

import grails.views.api.GrailsView
import grails.views.api.GrailsViewHelper
import grails.web.mapping.LinkGenerator
import groovy.transform.CompileStatic
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException

/**
 * Default methods for views, additional methods can be added via traits
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class DefaultGrailsViewHelper implements GrailsViewHelper {

    final GrailsView view


    DefaultGrailsViewHelper(GrailsView view) {
        this.view = view
    }

    LinkGenerator getLinkGenerator() {
        return view.linkGenerator
    }

    @Override
    String resource(Map params) {
        ensureAbsolute(params)
        getLinkGenerator().resource(params)
    }

    @Override
    String link(Map params) {
        ensureAbsolute(params)
        getLinkGenerator().link(params)
    }

    @Override
    String link(Map params, String encoding) {
        ensureAbsolute(params)
        getLinkGenerator().link(params, encoding)
    }

    @Override
    String getContextPath() {
        getLinkGenerator().contextPath
    }

    @Override
    String getServerBaseURL() {
        getLinkGenerator().serverBaseURL
    }

    @Override
    String message(Map arguments) {
        Object error = arguments.error ?: arguments.message
        if (error) {
            try {
                if (error instanceof MessageSourceResolvable) {
                    return view.messageSource.getMessage(error, view.locale)
                } else {
                    return view.messageSource.getMessage(error.toString(), null, view.locale)
                }
            }
            catch (NoSuchMessageException e) {
                if (error instanceof MessageSourceResolvable) {
                    return ((MessageSourceResolvable)error).codes[0]
                }
                else {
                    return error?.toString()
                }
            }
        }
        def args = arguments.args
        def code = arguments.code?.toString()
        def defaultMessage = arguments.default?.toString() ?: code
        if(code != null) {
            if(args != null) {
                if(args instanceof List) {
                    args = ((List)args).toArray()
                }
                else if(!args.getClass().isArray()) {
                    args = [args] as Object[]
                }
                return view.messageSource.getMessage( code, (Object[])args, defaultMessage, view.locale)
            }
            else {
                return view.messageSource.getMessage( code, null, defaultMessage, view.locale)
            }
        }
        return defaultMessage
    }

    protected void ensureAbsolute(Map params) {
        if (!params.containsKey(ATTRIBUTE_ABSOLUTE)) {
            params.put(ATTRIBUTE_ABSOLUTE, view.viewTemplate.useAbsoluteLinks)
        }
    }
}
