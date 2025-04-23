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
package org.grails.web.servlet;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.util.UrlPathHelper;

/**
 * Extends the default Spring UrlPathHelper and makes methods Grails path aware.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class GrailsUrlPathHelper extends UrlPathHelper {

    public static final String GRAILS_DISPATCH_EXTENSION = ".dispatch";
    public static final String GRAILS_SERVLET_PATH = "/grails";

    @Override
    public String getPathWithinApplication(HttpServletRequest request) {
        String uri = super.getPathWithinApplication(request).trim();
        if (uri.startsWith(GRAILS_SERVLET_PATH)) {
            uri = uri.substring(GRAILS_SERVLET_PATH.length());
        }
        if (uri.endsWith(GRAILS_DISPATCH_EXTENSION)) {
            return uri.substring(0,uri.length()- GRAILS_DISPATCH_EXTENSION.length());
        }
        return uri;
    }
}
