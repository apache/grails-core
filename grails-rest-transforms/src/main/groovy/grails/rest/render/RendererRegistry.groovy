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

package grails.rest.render

import grails.web.mime.MimeType

/**
 *
 * @author Graeme Rocher
 * @since 2.3
 */
interface RendererRegistry {

    /**
     * Adds a new renderer
     *
     * @param renderer The renderer to add
     */
    public <T> void addRenderer(Renderer<T> renderer)

    /**
     * Adds a default renderer, which are fall backs if the type is not known
     *
     * @param renderer The renderer to add
     */
    public void addDefaultRenderer(Renderer<Object> renderer)

    /**
     * Adds a container renderer
     *
     * @param objectType The object type
     * @param renderer The renderer
     */
    public void addContainerRenderer(Class objectType, Renderer renderer)

    /**
     * Finds a renderer
     *
     * @param contentType The content type
     * @param object The object
     *
     * @return The renderer
     */
    public <T> Renderer<T> findRenderer(MimeType contentType, T object)

    /**
     * Finds a renderer for a container (List, Errors, Map etc.) for another object
     *
     * @param contentType The content type
     * @param containerType The container type
     * @param object The object to render, an instance of the container (list, map etc.)
     * @return A renderer or null if non exists
     */
    public <C, T> Renderer<C> findContainerRenderer(MimeType contentType, Class<C> containerType, T object)

    /**
     * Whether the specified class is a container (list, map etc.)
     *
     * @param aClass The class
     * @return True if it is
     */
    boolean isContainerType(Class<?> aClass)
}
