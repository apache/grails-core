/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.web.pages;

import groovy.lang.GroovyObject;

/**
 * Provides methods to lookup URIs of views and templates.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public interface GroovyPagesUriService {

    String BEAN_ID = "groovyPagesUriService";

    /**
     * Obtains a template name of the given controller name and template name
     *
     * @param controllerName The controller name
     * @param templateName The template name
     * @return The template URI
     */
    String getTemplateURI(String controllerName, String templateName);

    String getDeployedViewURI(String controllerName, String viewName);

    String getNoSuffixViewURI(GroovyObject controller, String viewName);

    String getNoSuffixViewURI(String controllerName, String viewName);

    String getTemplateURI(GroovyObject controller, String templateName);

    void clear();

    String getTemplateURI(String controllerName, String templateName, boolean includeExtension);

    String getAbsoluteTemplateURI(String templateName, boolean includeExtension);

    String getAbsoluteViewURI(String viewName);

    String getDeployedAbsoluteViewURI(String viewName);

    /**
     * Obtains a view name for the given controller name and template name
     *
     * @param controllerName The controller name
     * @param viewName The view name
     * @return The view URI
     */
    String getViewURI(String controllerName, String viewName);

    String getTemplateURI(GroovyObject controller, String templateName, boolean includeExtension);
}
