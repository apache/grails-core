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

package org.apache.grails.web.layout;

import java.util.Iterator;

import com.opensymphony.module.sitemesh.Decorator;
import com.opensymphony.sitemesh.webapp.decorator.NoDecorator;

/**
 * Grails version of Sitemesh's NoDecorator
 *
 * @author Lari Hotari, Sagire Software Oy
 */
public class GrailsNoDecorator extends NoDecorator implements Decorator {

    public String getPage() {
        return null;
    }

    public String getName() {
        return null;
    }

    public String getURIPath() {
        return null;
    }

    public String getRole() {
        return null;
    }

    public String getInitParameter(String paramName) {
        return null;
    }

    @SuppressWarnings("rawtypes")
    public Iterator getInitParameterNames() {
        return null;
    }
}
