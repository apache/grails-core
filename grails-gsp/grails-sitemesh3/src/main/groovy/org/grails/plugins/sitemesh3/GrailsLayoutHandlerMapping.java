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

package org.grails.plugins.sitemesh3;

import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.mvc.ParameterizableViewController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

public class GrailsLayoutHandlerMapping extends AbstractHandlerMapping {
    @Autowired
    GrailsConventionGroovyPageLocator groovyPageLocator;

    Map<String, ParameterizableViewController> layoutCache = new HashMap<>();

    public GrailsLayoutHandlerMapping() {
        setOrder(-6);
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        if (request.getAttribute("jakarta.servlet.forward.request_uri") == null) {
            return null; // only handle forwarded requests.
        }
        String servletPath = request.getServletPath();
        if (servletPath.startsWith("/layouts")) {
            ParameterizableViewController pvc = layoutCache.get(servletPath);
            if (pvc == null) {
                if (groovyPageLocator.findViewByPath(servletPath) == null) {
                    throw new ResponseStatusException(NOT_FOUND, "Unable to find resource "+servletPath);
                }
                pvc = new ParameterizableViewController();
                pvc.setSupportedMethods(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name());
                pvc.setViewName(servletPath);
                layoutCache.put(servletPath, pvc);
            }
            return pvc;
        }
        return null;
    }
}