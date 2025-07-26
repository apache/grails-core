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
package grails.rest.render.xml

import groovy.transform.CompileStatic

import grails.converters.XML
import grails.rest.render.ContainerRenderer
import grails.rest.render.RenderContext
import grails.web.mime.MimeType

/**
 *
 * A XML renderer for a collection of objects
 *
 * @since 2.3.1
 *
 */
@CompileStatic
class XmlCollectionRenderer extends XmlRenderer implements ContainerRenderer {
    final Class componentType

    XmlCollectionRenderer(Class componentType) {
        super(Collection)
        this.componentType = componentType
    }

    public XmlCollectionRenderer(Class targetType, MimeType... mimeTypes) {
        super(targetType, mimeTypes)
    }

    @Override
    protected void renderXml(XML converter, RenderContext context) {
        converter.setExcludes(componentType, excludes ?: context.excludes)
        converter.setIncludes(componentType, includes != null ? includes : context.includes)
        converter.render(context.getWriter())
    }
}
