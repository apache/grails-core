/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.servlet.mvc;

import org.grails.core.artefact.ControllerArtefactHandler;
import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Default implementation that uses the web request to obtain information about the currently
 * executing request.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class DefaultRequestStateLookupStrategy implements GrailsRequestStateLookupStrategy {

    public static final String DEFAULT_REQUEST_ENCODING = "UTF-8";
    private GrailsApplication grailsApplication;
    private GrailsWebRequest webRequest;

    public DefaultRequestStateLookupStrategy() {
        // default
    }

    public DefaultRequestStateLookupStrategy(GrailsWebRequest webRequest) {
        this.webRequest = webRequest;
    }

    @Autowired
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    public String getContextPath() {
        final GrailsWebRequest req = getWebRequest();
        if (req != null) {
            return req.getContextPath();
        }
        return null;
    }

    public String getCharacterEncoding() {
        final GrailsWebRequest req = getWebRequest();
        if (req != null) {
            return req.getCurrentRequest().getCharacterEncoding();
        }
        return DEFAULT_REQUEST_ENCODING;
    }

    @Override
    public String getHttpMethod() {
        final GrailsWebRequest req = getWebRequest();
        if (req != null) {
            return req.getCurrentRequest().getMethod();
        }
        return null;
    }

    public String getControllerName() {
        final GrailsWebRequest req = getWebRequest();
        return getControllerNameInternal(req);
    }

    private String getControllerNameInternal(GrailsWebRequest req) {
        if (req != null) {
            return req.getControllerName();
        }
        return null;
    }

    public String getControllerNamespace() {
        final GrailsWebRequest req = getWebRequest();
        if (req != null) {
            return req.getControllerNamespace();
        }
        return null;
    }

    public String getActionName() {
        final GrailsWebRequest req = getWebRequest();
        if (req != null) {
            String actionName = req.getActionName();
            if (actionName == null) {
                if (grailsApplication == null) {
                    grailsApplication = req.getAttributes().getGrailsApplication();
                }
                if (grailsApplication != null) {
                    final String controllerName = getControllerNameInternal(req);
                    return getActionName(grailsApplication,controllerName);
                }
            }
        }
        return null;
    }

    private String getActionName(GrailsApplication application, String controllerName) {
        if (application != null) {
            final GrailsControllerClass controllerClass = (GrailsControllerClass) application.getArtefactByLogicalPropertyName(ControllerArtefactHandler.TYPE, controllerName);
            if (controllerClass != null) {
                return controllerClass.getDefaultAction();
            }
        }
        return null;
    }

    public String getActionName(String controllerName) {
        if (controllerName != null) {
            if (grailsApplication == null) {
                final GrailsWebRequest grailsWebRequest = getWebRequest();
                if (grailsWebRequest!= null)
                    grailsApplication = grailsWebRequest.getAttributes().getGrailsApplication();

            }
            return getActionName(grailsApplication, controllerName);
        }
        return null;
    }

    public GrailsWebRequest getWebRequest() {
        return webRequest != null ? webRequest : GrailsWebRequest.lookup();
    }
}
