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
package org.grails.plugins.web.rest.render

import grails.rest.render.RenderContext
import grails.rest.render.Renderer

/**
 * A {@link Renderer} that renders some values only, such as one that renders with a view and has no other way to render
 * a value. {@code respond} asks it before choosing it and, when it cannot render the value, tries the client's next
 * type, as it does for a type no renderer is registered for, as Spring MVC asks a message converter whether it can write
 * a value.
 *
 * @since 9.0
 */
interface SelectiveRenderer {

    /**
     * Whether the renderer renders the object in the context it would be rendered in.
     *
     * @param object the value {@code respond} renders
     * @param context the context it renders it in
     * @return {@code false} when the renderer cannot render the value
     */
    boolean canRender(Object object, RenderContext context)
}
