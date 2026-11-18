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

import grails.rest.render.Renderer

/**
 * Marks a {@link Renderer} bean that a plugin contributes as the default for its MIME types.
 *
 * <p>{@link DefaultRendererRegistry} registers it as it registers the renderers it creates itself:
 * it is consulted only after the renderers registered for the class hierarchy and interfaces of the
 * object. Every other renderer bean, including one whose target type is {@code Object}, keeps
 * precedence over it. For its MIME types it replaces the registry's own default renderer, such as
 * the JSON or HTML one, and a renderer added as a default once the registry is initialized replaces
 * it in turn. A {@link grails.rest.render.ContainerRenderer} marked so takes its container
 * type, component type and MIME types as the registry's own renderers of validation errors take
 * theirs: over the container renderer beans, as in Grails 8, and below a renderer added once the
 * registry is initialized.</p>
 *
 * @since 9.0
 */
interface FallbackRenderer {}
