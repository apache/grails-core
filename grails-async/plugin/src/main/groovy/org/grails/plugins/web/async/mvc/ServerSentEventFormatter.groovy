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
package org.grails.plugins.web.async.mvc

import groovy.transform.CompileStatic

import org.springframework.http.codec.ServerSentEvent

import grails.async.web.StreamItemRenderer

/**
 * Formats the items a stream emits as server-sent events.
 *
 * <p>A {@link ServerSentEvent} is written with its id, event name, retry interval and comment; any
 * other item becomes the data of an unnamed event. The data text comes from the first
 * {@link StreamItemRenderer} that handles the item, or from {@code String.valueOf} when none does.
 * Every line of the text is sent as its own {@code data:} line, so a client assembles exactly the text
 * that was emitted, line breaks included. Every stream ends with an event named {@code complete} whose
 * data says why: empty when the publisher completed, {@link #TIMEOUT_REASON} when the request timed out
 * and {@link #ERROR_REASON} when the stream failed. The event name is not {@code error}, which an
 * {@code EventSource} also dispatches for its own connection failures, and a failure's details stay in
 * the server log.</p>
 *
 * @since 8.1
 */
@CompileStatic
class ServerSentEventFormatter {

    public static final String COMPLETE_EVENT = 'complete'
    public static final String TIMEOUT_REASON = 'timeout'
    public static final String ERROR_REASON = 'error'

    private final List<StreamItemRenderer> renderers

    ServerSentEventFormatter(List<StreamItemRenderer> renderers) {
        this.renderers = List.copyOf(renderers)
    }

    /**
     * @return the wire form of the event for the item, ending with the blank line that dispatches it
     */
    String format(Object item) {
        StringBuilder out = new StringBuilder()
        if (item instanceof ServerSentEvent) {
            ServerSentEvent<?> event = (ServerSentEvent<?>) item
            String comment = event.comment()
            if (comment != null) {
                for (String line : lines(normalise(comment))) {
                    out.append(':').append(line).append('\n')
                }
            }
            // Spring's builder refuses an id or event name that holds a line break, so neither can forge a field
            if (event.id() != null) {
                out.append('id: ').append(event.id()).append('\n')
            }
            if (event.event() != null) {
                out.append('event: ').append(event.event()).append('\n')
            }
            if (event.retry() != null) {
                out.append('retry: ').append(event.retry().toMillis()).append('\n')
            }
            if (event.data() != null) {
                appendData(out, text(event.data()))
            }
        }
        else {
            appendData(out, text(item))
        }
        out.append('\n').toString()
    }

    /**
     * @param reason why the stream ended: empty when the publisher completed, {@link #TIMEOUT_REASON} when
     * the request timed out, {@link #ERROR_REASON} when the stream failed
     * @return the {@code complete} event that tells a client to stop listening instead of reconnecting
     */
    String formatCompletion(String reason) {
        StringBuilder out = new StringBuilder()
        out.append('event: ').append(COMPLETE_EVENT).append('\n')
        appendData(out, reason ?: '')
        out.append('\n').toString()
    }

    protected String text(Object item) {
        for (StreamItemRenderer renderer : renderers) {
            String rendered = renderer.render(item)
            if (rendered != null) {
                return rendered
            }
        }
        String.valueOf(item)
    }

    private static void appendData(StringBuilder out, String text) {
        // A client joins the data lines with line breaks and drops the last one, so the trailing empty
        // segment of a text that ends with a line break is exactly what keeps that line break.
        for (String line : lines(normalise(text))) {
            out.append('data: ').append(line).append('\n')
        }
    }

    private static String normalise(String text) {
        text.replace('\r\n', '\n').replace('\r', '\n')
    }

    private static String[] lines(String text) {
        text.split('\n', -1)
    }

}
