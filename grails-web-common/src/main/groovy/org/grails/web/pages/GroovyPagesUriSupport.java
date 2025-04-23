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
package org.grails.web.pages;

import grails.util.GrailsNameUtils;
import grails.web.pages.GroovyPagesUriService;
import groovy.lang.GroovyObject;

import org.grails.buffer.FastStringWriter;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.web.servlet.mvc.GrailsWebRequest;

/**
 * Methods to establish template names, paths and so on.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public class GroovyPagesUriSupport implements GroovyPagesUriService {

    public static final String PATH_TO_VIEWS = "/WEB-INF/grails-app/views";
    private static final char SLASH = '/';
    private static final String SLASH_STR = "/";
    private static final String SLASH_UNDR = "/_";
    private static final String BLANK = "";
    private static final String UNDERSCORE = "_";
    protected static final String EXTENSION = ".gsp";    
    protected static final String SUFFIX = ".gsp";
    public static final String RELATIVE_STRING = "../";

    /**
     * Obtains a template URI for the given controller instance and template name
     * @param controller The controller instance
     * @param templateName The template name
     * @return The template URI
     */
    public String getTemplateURI(GroovyObject controller, String templateName) {
        return getTemplateURI(getLogicalControllerName(controller),templateName);
    }

    @Override
    public String getTemplateURI(GroovyObject controller, String templateName, boolean includeExtension) {
        return getTemplateURI(getLogicalControllerName(controller),templateName, includeExtension);
    }

    public void clear() {
        // do nothing
    }

    /**
     * Obtains a view URI of the given controller and view name
     * @param controller The name of the controller
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getViewURI(GroovyObject controller, String viewName) {
        return getViewURI(getLogicalControllerName(controller), viewName);
    }

    /**
     * Obtains a view URI of the given controller and view name without the suffix
     * @param controller The name of the controller
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getNoSuffixViewURI(GroovyObject controller, String viewName) {
        return getNoSuffixViewURI(getLogicalControllerName(controller), viewName);
    }

    public String getLogicalControllerName(GroovyObject controller) {
        if(controller == null) {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            return webRequest != null ? webRequest.getControllerName() : null;
        }
        else {
            String simpleName = controller.getClass().getSimpleName();
            if(!simpleName.endsWith(ControllerArtefactHandler.TYPE)) {
                GrailsWebRequest webRequest = GrailsWebRequest.lookup();
                return webRequest != null ? webRequest.getControllerName() : null;
            }
            else {
                return GrailsNameUtils.getLogicalPropertyName(simpleName, ControllerArtefactHandler.TYPE);
            }
        }
    }

    /**
     * Obtains the URI to a template using the controller name and template name
     * @param controllerName The controller name
     * @param templateName The template name
     * @return The template URI
     */
    public String getTemplateURI(String controllerName, String templateName) {
        return getTemplateURI(controllerName, templateName, true);
    }
    /**
     * Obtains the URI to a template using the controller name and template name
     * @param controllerName The controller name
     * @param templateName The template name
     * @param includeExtension The flag to include the template extension
     * @return The template URI
     */
    @Override
    public String getTemplateURI(String controllerName, String templateName, boolean includeExtension) {
        if (templateName.startsWith(SLASH_STR)) {
            return getAbsoluteTemplateURI(templateName, includeExtension);
        }
        else if(templateName.startsWith(RELATIVE_STRING)) {
            return getRelativeTemplateURIInternal(templateName, includeExtension);
        }

        FastStringWriter buf = new FastStringWriter();
        String pathToTemplate = BLANK;

        int lastSlash = templateName.lastIndexOf(SLASH);
        if (lastSlash > -1) {
            pathToTemplate = templateName.substring(0, lastSlash + 1);
            templateName = templateName.substring(lastSlash + 1);
        }
        if(controllerName != null) {
            buf.append(SLASH)
                    .append(controllerName);
        }
        buf.append(SLASH)
                .append(pathToTemplate)
                .append(UNDERSCORE)
                .append(templateName);

        if(includeExtension) {
            return buf.append(EXTENSION).toString();
        }
        else {
            return buf.toString();
        }
    }

    /**
     * Used to resolve template names that are not relative to a controller.
     *
     * @param templateName The template name normally beginning with /
     * @param includeExtension The flag to include the template extension
     * @return The template URI
     */
    public String getAbsoluteTemplateURI(String templateName, boolean includeExtension) {
        FastStringWriter buf = new FastStringWriter();
        String tmp = templateName.substring(1,templateName.length());
        if (tmp.indexOf(SLASH) > -1) {
            buf.append(SLASH);
            int i = tmp.lastIndexOf(SLASH);
            buf.append(tmp.substring(0, i));
            buf.append(SLASH_UNDR);
            buf.append(tmp.substring(i + 1,tmp.length()));
        }
        else {
            buf.append(SLASH_UNDR);
            buf.append(templateName.substring(1,templateName.length()));
        }
        if (includeExtension) {
            buf.append(EXTENSION);
        }
        String uri = buf.toString();
        buf.close();
        return uri;
    }


    /**
     * Obtains a view URI of the given controller name and view name
     * @param controllerName The name of the controller
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getViewURI(String controllerName, String viewName) {
        FastStringWriter buf = new FastStringWriter();

        return getViewURIInternal(controllerName, viewName, buf, true);
    }

    /**
     * Obtains a view URI that is not relative to any given controller
     *
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getAbsoluteViewURI(String viewName) {
        FastStringWriter buf = new FastStringWriter();
        return getAbsoluteViewURIInternal(viewName, buf, true);
    }

    /**
     * Obtains a view URI of the given controller name and view name without the suffix
     * @param controllerName The name of the controller
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getNoSuffixViewURI(String controllerName, String viewName) {
        FastStringWriter buf = new FastStringWriter();

        return getViewURIInternal(controllerName, viewName, buf, false);
    }

    /**
     * Obtains a view URI when deployed within the /WEB-INF/grails-app/views context
     * @param controllerName The name of the controller
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getDeployedViewURI(String controllerName, String viewName) {
        FastStringWriter buf = new FastStringWriter(PATH_TO_VIEWS);
        return getViewURIInternal(controllerName, viewName, buf, true);
    }

    /**
     * Obtains a view URI when deployed within the /WEB-INF/grails-app/views context
     * @param viewName The name of the view
     * @return The view URI
     */
    public String getDeployedAbsoluteViewURI(String viewName) {
        FastStringWriter buf = new FastStringWriter(PATH_TO_VIEWS);
        return getAbsoluteViewURIInternal(viewName, buf, true);
    }

    private String getViewURIInternal(String viewPathPrefix, String viewName, FastStringWriter buf, boolean includeSuffix) {
        if (viewName != null && (viewName.startsWith(SLASH_STR) )) {
            return getAbsoluteViewURIInternal(viewName, buf, includeSuffix);
        }
        else if(viewName.startsWith(RELATIVE_STRING)) {
            return getRelativeViewURIInternal(viewName, buf, includeSuffix);
        }

        if (viewPathPrefix != null) {
            buf.append(SLASH).append(viewPathPrefix);
        }
        if (viewName != null) {
            buf.append(SLASH).append(viewName);
        }

        return includeSuffix ? buf.append(SUFFIX).toString() : buf.toString();
    }

    private String getRelativeViewURIInternal(String viewName, FastStringWriter buf, boolean includeSuffix) {
        String tmp = viewName.substring(RELATIVE_STRING.length() - 1, viewName.length());
        buf.append(tmp);
        if(includeSuffix) {
            buf.append(SUFFIX);
        }
        return buf.toString();
    }

    private String getAbsoluteViewURIInternal(String viewName, FastStringWriter buf, boolean includeSuffix) {
        String tmp = viewName.substring(1,viewName.length());
        if (tmp.indexOf(SLASH) > -1) {
            buf.append(SLASH);
            buf.append(tmp.substring(0,tmp.lastIndexOf(SLASH)));
            buf.append(SLASH);
            buf.append(tmp.substring(tmp.lastIndexOf(SLASH) + 1,tmp.length()));
        }
        else {
            buf.append(SLASH);
            buf.append(viewName.substring(1,viewName.length()));
        }
        if (includeSuffix) {
            buf.append(SUFFIX);
        }
        return buf.toString();
    }

    private String getRelativeTemplateURIInternal(String templateName, boolean includeSuffix) {
        String tmp = templateName.substring(RELATIVE_STRING.length() , templateName.length());
        FastStringWriter buf = new FastStringWriter();
        buf.append("/_");
        buf.append(tmp);
        if(includeSuffix) {
            buf.append(SUFFIX);
        }
        return buf.toString();
    }
}
