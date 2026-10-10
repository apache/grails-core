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
 * precedence over it.</p>
 *
 * @since 9.0
 */
interface FallbackRenderer extends Renderer<Object> {}
