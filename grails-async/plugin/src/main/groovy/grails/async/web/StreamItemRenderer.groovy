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
package grails.async.web

/**
 * Renders one item that a {@link org.reactivestreams.Publisher} returned from a controller action emits
 * as the text of a server-sent event.
 *
 * <p>Every bean of this type is consulted in {@link org.springframework.core.Ordered} order and the first
 * text wins, so a plugin teaches the stream about its own types by registering a renderer, and a
 * renderer with a higher precedence than another takes over the types both handle. The two built-in
 * renderers, which render text as-is and, when the application has Jackson, anything else as JSON, are
 * always consulted last, so a renderer wins over them whether or not it is {@code Ordered}.</p>
 *
 * @since 8.1
 */
interface StreamItemRenderer {

    /**
     * @param item the item the publisher emitted, which may be {@code null}
     * @return the text to send for the item, or {@code null} when this renderer does not handle it
     */
    String render(Object item)

}
