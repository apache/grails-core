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
package org.apache.grails.datastore.core.querytail;

/**
 * Reads what the web framework that routed a request resolved it to, for the {@code query-tail} capture: the page
 * the statements of the request are counted under, and how the path of the request reads with the values in it
 * replaced. The capture finds the readers on its class path with {@link java.util.ServiceLoader}, so that it reads
 * the requests of a framework without depending on it, and asks each in turn until one has resolved the request;
 * Grails URL mapping provides one. A request no reader resolves has no page, and its path is recorded as the pattern
 * a Spring MVC handler mapping matched, or by its shape alone.
 *
 * <p>A reader is asked for every statement a request runs, which may be hundreds, so one that has work to do keeps
 * what it found in an attribute of the request, and finds it there again until what it found it from changes. The
 * capture reads the path of a request again only when the request moves on, or when the target it is given is
 * another one, so a reader returns the same target for as long as the request is resolved to the same thing.</p>
 *
 * <p>Recording never fails the statement it records: a reader that throws is passed over, and the request is
 * recorded as if it had resolved nothing.</p>
 *
 * @since 9.0
 */
public interface QueryTailRequestReader {

    /**
     * Reads the request on the current thread.
     *
     * @param request the attributes of the request
     * @return what the framework resolved the request to, or null when it has resolved it to nothing this reader knows
     */
    QueryTailRequestTarget read(Attributes request);

    /**
     * The attributes of a request, where a framework keeps what it resolved the request to.
     */
    interface Attributes {

        /**
         * @param name the name of an attribute
         * @return its value, or null when the request has none
         */
        Object getAttribute(String name);

        /**
         * Keeps a value on the request, for the later statements of the request to find.
         *
         * @param name the name of the attribute, which no other reader or framework uses
         * @param value the value
         */
        void setAttribute(String name, Object value);
    }
}
