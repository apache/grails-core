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
package grails.rest.render

import grails.web.mime.MimeType
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

/**
 * Passed to a renderer to provide context information
 *
 * @author Graeme Rocher
 * @since 2.3
 */
public interface RenderContext {

    /**
     * Which properties should be included in rendering
     * @return A list of includes
     */
    List<String> getIncludes()

    /**
     * Which properties should be excluded from rendering
     * @return A list of excludes
     */
    List<String> getExcludes()


    /**
     * @return Arguments passed by the user
     */
    Map<String, Object> getArguments()

    /**
     * @return The path the the resource
     */
    String getResourcePath()
    /**
     * @return Returns the mime type accepted by the client or null if non specified
     */
    MimeType getAcceptMimeType()
    /**
     * @return The locale
     */
    Locale getLocale()
    /**
     * @return The writer to render to
     */
    Writer getWriter()

    /**
     * @return The HTTP method
     */
    HttpMethod getHttpMethod();

    /**
     * @param status The status to set
     */
    void setStatus(HttpStatus status)

    /**
     * Sets the content type of the rendered response
     *
     * @param contentType The content type
     */
    void setContentType(String contentType)

    /**
     * The view to use for the response
     *
     * @param viewName The view name
     */
    void setViewName(String viewName)

    /**
     * @return The view name to use
     */
    String getViewName()

    /**
     * The model to use for the response
     * @param model The model
     */
    void setModel(Map model)

    /**
     * @return The current action name
     */
    String getActionName()

    /**
     * @return The current controller name
     */
    String getControllerName()

    /**
     * @return The current controller name
     */
    String getControllerNamespace()

    /**
     * Returns true if the getWriter() method was called
     */
    boolean wasWrittenTo()
}
