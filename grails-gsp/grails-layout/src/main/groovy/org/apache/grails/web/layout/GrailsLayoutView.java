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

import com.opensymphony.sitemesh.ContentProcessor;
import com.opensymphony.sitemesh.webapp.SiteMeshWebAppContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.springframework.web.servlet.View;

import java.util.Map;

public class GrailsLayoutView extends EmbeddedGrailsLayoutView {

    private final ContentProcessor contentProcessor;

    public GrailsLayoutView(GroovyPageLayoutFinder groovyPageLayoutFinder, View innerView, ContentProcessor contentProcessor) {
        super(groovyPageLayoutFinder, innerView);
        this.contentProcessor = contentProcessor;
    }

    @Override
    protected GrailsContentBufferingResponse createContentBufferingResponse(Map<String, Object> model,
                                                                            GrailsWebRequest webRequest, HttpServletRequest request, HttpServletResponse response) {
        return new GrailsContentBufferingResponse(response, contentProcessor, new SiteMeshWebAppContext(request, response, webRequest.getServletContext()));
    }
}
