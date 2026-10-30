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
package org.grails.plugins.xml

import groovy.transform.CompileStatic

import org.grails.plugins.web.rest.render.FallbackRenderer
import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer

/**
 * Renders any object as XML when no other renderer applies.
 *
 * <p>A {@link FallbackRenderer} so that it can be contributed as a bean and still keep the
 * precedence it had when the registry created it: renderers an application registers for a class,
 * an interface or {@code Object} are consulted first.</p>
 *
 * @since 9.0
 */
@CompileStatic
class XmlFallbackRenderer extends DefaultXmlRenderer<Object> implements FallbackRenderer {

    XmlFallbackRenderer() {
        super(Object)
    }
}
