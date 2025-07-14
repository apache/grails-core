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
package grails.web.mapping;

import grails.gorm.validation.Constrained;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * <p>Defines a URL mapping. A URL mapping is a mapping between a URI such as /book/list and
 * a controller, action and/or id.</p>
 *
 * <p>A UrlMapping should implement Comparable so that UrlMapping instances can be ordered to allow for precendence rules.
 * In other words the URL /book/list should be matched before /book/* as the wildcard is of lesser precedence. By implementing
 * Comparable this can be allowed for.
 * </p>
 *
 * @author Graeme Rocher
 * @since 0.5
 */
@SuppressWarnings("rawtypes")
public interface UrlMapping extends Comparable, UrlCreator {

    String WILDCARD = "*";
    String CAPTURED_WILDCARD = "(*)";
    String OPTIONAL_EXTENSION_WILDCARD = "(.(*))";
    String SLASH = "/";
    char QUESTION_MARK = '?';
    char AMPERSAND = '&';
    String DOUBLE_WILDCARD = "**";
    String CAPTURED_DOUBLE_WILDCARD = "(**)";

    /**
     * The controller this mapping matches
     */
    String CONTROLLER = "controller";
    /**
     * The action this mapping matches
     */
    String ACTION = "action";
    /**
     * The HTTP method this mapping matches
     */
    String HTTP_METHOD = "method";

    /**
     * Redirect information for this url mapping.
     */
    String REDIRECT_INFO = "redirect";

    /**
     * Constant used to define a Url mapping that matches any HTTP method
     */
    String ANY_HTTP_METHOD = "*";

    /**
     * The version of the URL mapping
     */
    String VERSION = "version";

    /**
     * Constant used to define a Url mapping that matches any HTTP method
     */
    String ANY_VERSION = "*";

    /**
     * The URI of the URL mapping
     */
    String URI = "uri";
    /**
     * The plugin of the URL Mapping
     */
    String PLUGIN = "plugin";
    /**
     * The namespace of the URL mapping
     */
    String NAMESPACE = "namespace";

    String VIEW = "view";

    String RESOURCES = "resources";

    String EXCLUDES = "excludes";

    String INCLUDES = "includes";

    String PERMANENT = "permanent";

    String EXCEPTION = "exception";

    Set<String> KEYWORDS = new HashSet<String>() {{
        add(CONTROLLER);
        add(ACTION);
        add(HTTP_METHOD);
        add(REDIRECT_INFO);
        add(VERSION);
        add(URI);
        add(PLUGIN);
        add(NAMESPACE);
        add(VIEW);
        add(RESOURCES);
        add(INCLUDES);
        add(PERMANENT);
        add(EXCEPTION);
    }};

    /**
     * Matches the given URI and returns an instance of the UrlMappingInfo interface or null
     * if a match couldn't be established
     *
     * @param uri The URI to match
     * @return An instance of UrlMappingInfo or null if the URI doesn't match
     */
    UrlMappingInfo match(String uri);

    /**
     * Retrieves the UrlMappingData instance that describes this UrlMapping
     *
     * @return The UrlMappingData instance
     */
    UrlMappingData getUrlData();

    /**
     * <p>The constraints that apply to this UrlMapping. Each constraint maps to a GString token in a
     * URL mapping in order. For example consider the URL:
     *
     * <pre>
     * <code>
     *     /blog/$author/$title/$year?/$month?/$day?
     * </code></pre>
     *
     * <p>This results in 5 ConstrainedProperty instances called author, title, year, month and day
     *
     * @return An array containing the ConstrainedProperty objects of this URLMapping
     */
    Constrained[] getConstraints();

    /**
     * Retrieves the controller name which is either a groovy.lang.Closure that evaluates the controller
     * name at runtime or a java.lang.String that represents the controller name
     *
     * @return The controller name as a {@link groovy.lang.Closure} or {@link java.lang.String}
     */
    Object getControllerName();

    /**
     * Retrieves the action name which is either a groovy.lang.Closure that evaluates the action
     * name at runtime or a java.lang.String that represents the action name
     *
     * @return The action name as a {@link groovy.lang.Closure} or {@link java.lang.String}
     */
    Object getActionName();

    /**
     * The name of the plugin this URL mapping relates to, if any
     *
     * @return The plugin name
     */
    Object getPluginName();

    /**
     * @return the name of the controller namespace
     */
    Object getNamespace();

    /**
     * Returns the name of the view to map to
     *
     * @return The view name
     */
    Object getViewName();

    /**
     * The HTTP method this URL mapping applies to. Will be null for all HTTP methods
     *
     * @return The HTTP method
     */
    String getHttpMethod();

    /**
     * @return The version of the URL mapping. Used for versioning of REST services
     */
    String getVersion();

    /**
     * Sets any parameter values that should be populated into the request
     *
     * @param parameterValues The parameter values to set
     */
    void setParameterValues(Map<String, Object> parameterValues);

    /**
     * Sets whether this UrlMapping should parse the request
     *
     * @param shouldParse True if it should
     */
    void setParseRequest(boolean shouldParse);

    /**
     * The name of the mapping in case of named URL mapping
     *
     * @return The mapping name
     */
    String getMappingName();

    /**
     * Sets the name of the URL mapping
     *
     * @param name The name of the URL mapping
     */
    void setMappingName(String name);

    /**
     * Whether the mapping has a runtime variable with the given name such as "/$foo"
     *
     * @param name The name of the variable
     * @return true if the mapping has the variable
     */
    boolean hasRuntimeVariable(String name);

    /**
     * The redirect information should be a String or a Map.  If it
     * is a String that string is the URI to redirect to.  If it is
     * a Map, that Map may contain any entries supported as arguments
     * to the dynamic redirect(Map) method on a controller.
     *
     * @return redirect information for this url mapping
     */
    Object getRedirectInfo();

    /**
     * Sets whether or not the mapping is defined in a plugin.
     *
     * @param pluginIndex The index of the plugin that defines this mapping
     */
    void setPluginIndex(int pluginIndex);

    /**
     * @return The plugin index or null
     */
    Integer getPluginIndex();

    /**
     * @return True if the URL mapping comes from a plugin
     */
    boolean isDefinedInPlugin();
}
