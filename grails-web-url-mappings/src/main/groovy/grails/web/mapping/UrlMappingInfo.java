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
package grails.web.mapping;

import java.util.Map;

import org.grails.web.servlet.mvc.GrailsWebRequest;

/**
 * Defines that data that was produced when matching a URI with a UrlMapping instance.
 *
 * @see UrlMapping
 * @author Graeme Rocher
 * @since 0.5
 */
public interface UrlMappingInfo {

    /**
     * The URI to map to. Note when the URI is specified it overrides any
     * explicit controller/action/id mappings. In other words you can either
     * specify the URI or the controller/action/id, but not both
     *
     * @return The URI to use
     */
    String getURI();

    /**
     * The HTTP method that this URL mapping maps to
     *
     * @return The http method
     */
    String getHttpMethod();

    /**
     * @return The version of the API (for REST)
     */
    String getVersion();

    /**
     * The name of the controller that the URL mapping maps to
     *
     * @return The name of the controller
     */
    String getControllerName();

    /**
     * The name of the action that the URL mappping maps to
     *
     * @return The name of the action or null if not known
     */
    String getActionName();

    /**
     * @return the namespace of the corresponding controller, null if none was specified
     */
    String getNamespace();

    /**
     * The name of the plugin that this UrlMappingInfo maps to
     *
     * @return The plugin name
     */
    String getPluginName();

    /**
     * The name of the view that the URL mappping maps to
     *
     * @return The name of the view or null if not known
     */
    String getViewName();

    /**
     * The id part of the URL mapping if any
     *
     * @return The id or null
     */
    String getId();

    /**
     * The parameters that were extracted from the URI that was matched
     *
     * @return A Map of parameters
     */
    @SuppressWarnings("rawtypes")
    Map getParameters();

    /**
     * Configure this UrlMappingInfo the for the given GrailsWebRequest
     *
     * @param webRequest  The GrailsWebRequest instance
     */
    void configure(GrailsWebRequest webRequest);

    /**
     * Returns true of the request body should be parsed. This typically happens
     * in the case of REST requests that parse JSON or XML packets
     *
     * @return true if it is
     */
    boolean isParsingRequest();

    /**
     * The redirect information should be a String or a Map.  If it
     * is a String that string is the URI to redirect to.  If it is
     * a Map, that Map may contain any entries supported as arguments
     * to the dynamic redirect(Map) method on a controller.
     *
     * @return redirect information for this url mapping, null if no redirect is specified
     */
    Object getRedirectInfo();

    /**
     * Retrieves the UrlMappingData (information about a parsed URL) if any
     *
     * @return The UrlMappingData instance
     */
    UrlMappingData getUrlData();
}
