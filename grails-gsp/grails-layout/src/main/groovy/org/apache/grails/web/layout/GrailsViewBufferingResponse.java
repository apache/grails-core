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
package org.apache.grails.web.layout;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.opensymphony.sitemesh.Content;
import com.opensymphony.sitemesh.ContentProcessor;
import com.opensymphony.sitemesh.SiteMeshContext;
import com.opensymphony.sitemesh.webapp.SiteMeshWebAppContext;

public class GrailsViewBufferingResponse extends GrailsContentBufferingResponse {

    private static class SimpleWebAppContext extends SiteMeshWebAppContext {
        public SimpleWebAppContext(HttpServletRequest request, HttpServletResponse response) {
            super(request, response, request.getServletContext());
        }
    }

    private static class SimpleHtmlOnlyContentProcessor implements ContentProcessor {
        @Override
        public Content build(final char[] data, SiteMeshContext context) throws IOException {
            return new GrailsHTMLPageParser().parseContent(data);
        }

        @Override
        public boolean handles(SiteMeshContext context) {
            return handles(context.getContentType());
        }

        @Override
        public boolean handles(String contentType) {
            return contentType != null && contentType.contains("html");
        }
    }

    public GrailsViewBufferingResponse(HttpServletRequest request, HttpServletResponse response) {
        super(response, new SimpleHtmlOnlyContentProcessor(), new SimpleWebAppContext(request, response));
    }
}
