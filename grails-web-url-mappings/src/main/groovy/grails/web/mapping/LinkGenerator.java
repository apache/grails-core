/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.web.mapping;

import grails.util.CollectionUtils;

import java.util.Map;
import java.util.Set;

/**
 * Generates links for a Grails application based on URL mapping rules and/or base context settings.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public interface LinkGenerator {

    String BEAN_NAME = "grailsLinkGenerator";
    String ATTRIBUTE_CONTROLLER = "controller";
    String ATTRIBUTE_RESOURCE = "resource";
    String ATTRIBUTE_ACTION = "action";
    String ATTRIBUTE_METHOD = "method";
    String ATTRIBUTE_URI = "uri";
    String ATTRIBUTE_RELATIVE_URI = "relativeUri";
    String ATTRIBUTE_INCLUDE_CONTEXT = "includeContext";
    String ATTRIBUTE_CONTEXT_PATH = "contextPath";
    String ATTRIBUTE_URL = "url";
    String ATTRIBUTE_BASE = "base";
    String ATTRIBUTE_ABSOLUTE = "absolute";
    String ATTRIBUTE_ID = "id";
    String ATTRIBUTE_FRAGMENT = "fragment";
    String ATTRIBUTE_PARAMS = "params";
    String ATTRIBUTE_MAPPING = "mapping";
    String ATTRIBUTE_EVENT = "event";
    String ATTRIBUTE_ELEMENT_ID = "elementId";
    String ATTRIBUTE_PLUGIN = "plugin";
    String ATTRIBUTE_NAMESPACE = "namespace";
    

    Set<String> LINK_ATTRIBUTES = CollectionUtils.newSet(
       ATTRIBUTE_RESOURCE,
       ATTRIBUTE_METHOD,
       ATTRIBUTE_CONTROLLER,
       ATTRIBUTE_ACTION,
       ATTRIBUTE_URI,
       ATTRIBUTE_RELATIVE_URI,
       ATTRIBUTE_CONTEXT_PATH,
       ATTRIBUTE_URL,
       ATTRIBUTE_BASE,
       ATTRIBUTE_ABSOLUTE,
       ATTRIBUTE_ID,
       ATTRIBUTE_FRAGMENT,
       ATTRIBUTE_PARAMS,
       ATTRIBUTE_MAPPING,
       ATTRIBUTE_EVENT,
       ATTRIBUTE_ELEMENT_ID,
       ATTRIBUTE_PLUGIN,
       ATTRIBUTE_NAMESPACE
       );

    Map<String, String> REST_RESOURCE_ACTION_TO_HTTP_METHOD_MAP = CollectionUtils.<String, String>newMap(
        "create", "GET",
        "save",   "POST",
        "show",   "GET",
        "index",  "GET",
        "edit",   "GET",
        "update", "PUT",
        "patch",  "PATCH",
        "delete", "DELETE"
    );

    Map<String, String> REST_RESOURCE_HTTP_METHOD_TO_ACTION_MAP = CollectionUtils.<String, String>newMap(
        "GET_ID", "show",
        "GET",    "index",
        "POST",   "save",
        "DELETE", "delete",
        "PUT",    "update",
        "PATCH",  "patch"
    );


    /**
     * Generates a link to a static resource for the given named parameters.
     *
     * Possible named parameters include:
     *
     * <ul>
     *    <li>base - The base path of the URL, typically an absolute server path</li>
     *    <li>contextPath - The context path to link to, defaults to the servlet context path</li>
     *    <li>dir - The directory to link to</li>
     *    <li>file - The file to link to (relative to the directory if specified)</li>
     *    <li>plugin - The plugin that provides the resource</li>
     *    <li>absolute - Whether the link should be absolute or not</li>
     * </ul>
     *
     * @param params The named parameters
     * @return The link to the static resource
     */
    String resource(@SuppressWarnings("rawtypes") Map params);

    /**
     * Generates a link to a controller, action or URI for the given named parameters.
     *
     * Possible named parameters include:
     *
     * <ul>
     *    <li>resource - If linking to a REST resource, the name of the resource or resource path to link to. Either 'resource' or 'controller' should be specified, but not both</li>
     *    <li>controller - The name of the controller to use in the link, if not specified the current controller will be linked</li>
     *    <li>action -  The name of the action to use in the link, if not specified the default action will be linked</li>
     *    <li>uri -  relative URI</li>
     *    <li>url -  A map containing the action,controller,id etc.</li>
     *    <li>base -  Sets the prefix to be added to the link target address, typically an absolute server URL. This overrides the behaviour of the absolute property, if both are specified.</li>
     *    <li>absolute -  If set to "true" will prefix the link target address with the value of the grails.serverURL property from Config, or http://localhost:&lt;port&gt; if no value in Config and not running in production.</li>
     *    <li>contextPath - The context path to link to, defaults to the servlet context path</li>
     *    <li>id -  The id to use in the link</li>
     *    <li>fragment -  The link fragment (often called anchor tag) to use</li>
     *    <li>params -  A map containing URL query parameters</li>
     *    <li>mapping -  The named URL mapping to use to rewrite the link</li>
     *    <li>event -  Webflow _eventId parameter</li>
     * </ul>
     * @param params The named parameters
     * @return The generator link
     */
    String link(@SuppressWarnings("rawtypes") Map params);

    /**
     * Generates a link to a controller, action or URI for the given named parameters.
     *
     * Possible named parameters include:
     *
     * <ul>
     *    <li>resource - If linking to a REST resource, the name of the resource or resource path to link to. Either 'resource' or 'controller' should be specified, but not both</li>
     *    <li>controller - The name of the controller to use in the link, if not specified the current controller will be linked</li>
     *    <li>action -  The name of the action to use in the link, if not specified the default action will be linked</li>
     *    <li>uri -  relative URI</li>
     *    <li>url -  A map containing the action,controller,id etc.</li>
     *    <li>base -  Sets the prefix to be added to the link target address, typically an absolute server URL. This overrides the behaviour of the absolute property, if both are specified.</li>
     *    <li>absolute -  If set to "true" will prefix the link target address with the value of the grails.serverURL property from Config, or http://localhost:&lt;port&gt; if no value in Config and not running in production.</li>
     *    <li>id -  The id to use in the link</li>
     *    <li>fragment -  The link fragment (often called anchor tag) to use</li>
     *    <li>params -  A map containing URL query parameters</li>
     *    <li>mapping -  The named URL mapping to use to rewrite the link</li>
     *    <li>event -  Webflow _eventId parameter</li>
     * </ul>
     * @param params The named parameters
     * @param encoding The character encoding to use
     * @return The generator link
     */
    String link(@SuppressWarnings("rawtypes") Map params, String encoding);

    /**
     * Obtains the context path from which this link generator is operating.
     *
     * @return The base context path
     */
    String getContextPath();

    /**
     * The base URL of the server used for creating absolute links.
     *
     * @return The base URL of the server
     */
    String getServerBaseURL();
}
