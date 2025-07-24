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

package org.grails.web.servlet.view

import groovy.transform.CompileStatic

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

/**
 * Performs the job of iterating across registered view resolvers and returning the first matching view similar to the
 * hard coded behavior in DispatcherServlet
 *
 * @author Graeme Rocher
 * @since 3.1.1
 */
@CompileStatic
class CompositeViewResolver {

    public static final String BEAN_NAME = "compositeViewResolver"

    @Autowired(required = false)
    List<ViewResolver> viewResolvers = []

    View resolveView(String viewName, Locale locale) {
        for(resolver in viewResolvers) {

            def view = resolver.resolveViewName(viewName, locale)
            if(view != null) {
                return view
            }
        }
        return null
    }
}
