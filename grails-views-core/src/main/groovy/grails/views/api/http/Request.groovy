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

package grails.views.api.http

import groovy.transform.CompileStatic

/**
 * Allows access to request properties
 */
@CompileStatic
interface Request {
    /**
     * @return The context path
     */
    String getContextPath()

    /**
     * @return The request method
     */
    String getMethod()

    /**
     * @return The request URI
     */
    String getUri()

    /**
     * @return The request content type
     */
    String getContentType()
    /**
     * @return The request character encoding
     */
    String getCharacterEncoding()

    /**
     * @return The header for the request
     */
    Collection<String> getHeaderNames()

    /**
     * Obtains the value of a header
     *
     * @param name The name of the header
     * @return The value of the header
     */
    String getHeader(String name)

    /**
     * Obtains all the values for the give header
     *
     * @param name The name of the header
     * @return all of the views
     */
    Collection<String> getHeaders(String name)

    /**
     * Obtains the value of an attribute
     *
     * @param name The name of the attribute
     * @return The value of the attribute
     */
    Object getAttribute(String name)

    /**
     * @return All of the attribute names
     */
    Collection<String> getAttributeNames()
}
