/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.rest.render.json

import grails.converters.JSON
import grails.rest.render.ContainerRenderer
import grails.rest.render.RenderContext
import groovy.transform.CompileStatic

import grails.web.mime.MimeType

/**
 * A JSON renderer for a collection of objects
 *
 * @since 2.3.1
 */
@CompileStatic
class JsonCollectionRenderer extends JsonRenderer implements ContainerRenderer {
    final Class componentType

    JsonCollectionRenderer(Class componentType) {
        super(Collection)
        this.componentType = componentType
    }

    public JsonCollectionRenderer(Class componentType, MimeType... mimeTypes) {
        super(Collection, mimeTypes)
        this.componentType = componentType
    }
    
    @Override
    protected void renderJson(JSON converter, RenderContext context) {
        converter.setExcludes(componentType, excludes != null ? excludes : context.excludes)
        converter.setIncludes(componentType, includes != null ? includes : context.includes)
        converter.render(context.getWriter())
    }
}
