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

import org.springframework.http.HttpStatus

/**
 * Allows control over the page response (headers, content type, status)
 */
@CompileStatic
interface Response {

    /**
     * Set a response header
     *
     * @param name The name of the header
     * @param value The value of the header
     */
    void header(String name, String value)

    /**
     * Set a single named value header
     * @param nameAndValue The name and value. Example header(foo:"bar")
     */
    void header(Map<String,String> nameAndValue)

    /**
     * Set multiple headers
     *
     * @param namesAndValues The names and values
     */
    void headers(Map<String,String> namesAndValues)

    /**
     * Set the response content type
     *
     * @param contentType
     */
    void contentType(String contentType)

    /**
     * Sets the response encoding
     *
     * @param encoding
     */
    void encoding(String encoding)

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(int status)

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(int status, String message)

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(HttpStatus status)

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(HttpStatus status, String message)
}
