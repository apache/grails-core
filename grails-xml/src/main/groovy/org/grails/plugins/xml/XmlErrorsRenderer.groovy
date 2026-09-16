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

import org.springframework.validation.Errors

import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer
import grails.rest.render.ContainerRenderer

/**
 * Renders validation errors as XML for any object type.
 *
 * <p>A {@link ContainerRenderer} so that it can be contributed as a bean: DefaultRendererRegistry
 * autowires every {@code Renderer} and routes container renderers to the container registry, which
 * keeps registration independent of which registry instance is created and when.</p>
 *
 * @since 8.0
 */
@CompileStatic
@SuppressWarnings('rawtypes')
class XmlErrorsRenderer extends DefaultXmlRenderer implements ContainerRenderer {

    XmlErrorsRenderer() {
        super(Errors)
    }

    /**
     * The registry keys a container renderer by (targetType, componentType); errors are rendered
     * for any object type, so the component type is Object while the target type stays Errors.
     */
    @Override
    Class getComponentType() {
        return Object
    }
}
