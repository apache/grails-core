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

package grails.views.api

import groovy.transform.CompileStatic

import grails.views.api.http.Parameters
import grails.views.api.http.Request
import grails.views.api.http.Response
import grails.views.api.internal.EmptyParameters

/**
 * A view that is rendered in the context of an HTTP request
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait HttpView extends View {

    /**
     * @return The page object
     */
    Response response

    /**
     * @return The request object
     */
    Request request

    /**
     * @return The same as {@link #getResponse()}
     */
    Request getPage() {
        request
    }

    /**
     * The parameters
     */
    Parameters params = new EmptyParameters()
}
