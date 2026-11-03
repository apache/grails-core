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

package org.apache.grails.web.layout

import groovy.transform.CompileStatic

import com.opensymphony.module.sitemesh.Decorator
import com.opensymphony.sitemesh.webapp.decorator.NoDecorator

/**
 * Grails version of Sitemesh's NoDecorator
 *
 * @author Lari Hotari, Sagire Software Oy
 */
@CompileStatic
class GrailsNoDecorator extends NoDecorator implements Decorator {

    String getPage() {
        return null
    }

    String getName() {
        return null
    }

    String getURIPath() {
        return null
    }

    String getRole() {
        return null
    }

    String getInitParameter(String paramName) {
        return null
    }

    @SuppressWarnings('rawtypes')
    Iterator getInitParameterNames() {
        return null
    }
}
