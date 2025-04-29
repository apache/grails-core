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

import org.grails.web.util.GrailsApplicationAttributes;
import groovy.lang.GroovyClassLoader;

import org.grails.core.artefact.ControllerArtefactHandler;
import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import org.grails.core.artefact.gsp.TagLibArtefactHandler;
import org.grails.support.MockApplicationContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;

import static org.junit.jupiter.api.Assertions.*;

public class GrailsApplicationAttributesTests {

    /*
     * Test method for 'org.grails.web.servlet.DefaultGrailsApplicationAttributes.getTemplateUri(String, ServletRequest)'
     */
    @Test
    public void testGetTemplateUri() {
         GrailsApplicationAttributes attrs = new DefaultGrailsApplicationAttributes(new MockServletContext());

         assertEquals("/_test.gsp",attrs.getTemplateUri("/test", new MockHttpServletRequest()));
         assertEquals("/shared/_test.gsp",attrs.getTemplateUri("/shared/test", new MockHttpServletRequest()));
    }

    /*
     * Test method for 'org.grails.web.servlet.DefaultGrailsApplicationAttributes.getViewUri(String, ServletRequest)'
     */
    @Test
    public void testGetViewUri() throws Exception {
        GrailsApplicationAttributes attrs = new DefaultGrailsApplicationAttributes(new MockServletContext());
        GroovyClassLoader gcl = new GroovyClassLoader();
        Class<?> controllerClass = gcl.parseClass("class TestController {\n" +
                "def controllerUri = '/test'\n" +
                "def controllerName = 'test'\n" +
                "}");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controllerClass.newInstance());

        assertEquals("/WEB-INF/grails-app/views/test/aView.gsp",attrs.getViewUri("aView", request));
        assertEquals("/WEB-INF/grails-app/views/shared.gsp",attrs.getViewUri("/shared", request));
    }

    private GrailsApplicationAttributes getAttributesForClasses(Class<?>[] classes, GroovyClassLoader gcl) {
        MockApplicationContext context = new MockApplicationContext();
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT,context);

        GrailsApplication app = new DefaultGrailsApplication(classes,gcl);
        app.initialise();
        context.registerMockBean(GrailsApplication.APPLICATION_ID,app);

        GrailsClass[] controllers = app.getArtefacts(ControllerArtefactHandler.TYPE);
        for (int i = 0; i < controllers.length; i++) {
            context.registerMockBean(controllers[i].getFullName(), controllers[i].newInstance());
        }

        GrailsClass[] taglibs = app.getArtefacts(TagLibArtefactHandler.TYPE);
        for (int i = 0; i < taglibs.length; i++) {
            context.registerMockBean(taglibs[i].getFullName(), taglibs[i].newInstance());
        }
        return new DefaultGrailsApplicationAttributes(servletContext);
    }
}
