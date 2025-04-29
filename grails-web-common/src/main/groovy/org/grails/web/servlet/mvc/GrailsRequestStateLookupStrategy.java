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
package org.grails.web.servlet.mvc;

/**
 * Strategy interface for obtaining details about the currently executing request
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public interface GrailsRequestStateLookupStrategy {

    /**
     * Obtains the context path to use from the request
     *
     * @return The context path
     */
    public String getContextPath();

    /**
     * The character encoding of the request
     *
     * @return The character encoding
     */
    public String getCharacterEncoding();
    /**
     * The controller name
     *
     * @return The controller name or null if not known
     */
    public String getControllerName();

    /**
     * The controller namespace
     *
     * @return The controller namespace or null if not known
     */
    public String getControllerNamespace();

    /**
     * The action name for the given controller name
     *
     * @param controllerName The controller name
     * @return The action name or null if not known
     */
    public String getActionName(String controllerName);
    /**
     * The action name
     *
     * @return The action name or null if not known
     */
    public String getActionName();

    /**
     * @return The HTTP method
     */
    public String getHttpMethod();

    /**
     * @return the current request
     */
    GrailsWebRequest getWebRequest();
}
