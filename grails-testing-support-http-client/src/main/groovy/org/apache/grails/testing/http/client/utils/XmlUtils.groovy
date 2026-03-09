/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.http.client.utils

import groovy.xml.MarkupBuilder

class XmlUtils {

    /**
     * Builds XML text using {@link groovy.xml.MarkupBuilder}.
     * <p>
     * The closure is cloned and delegated to a {@link groovy.xml.MarkupBuilder} with
     * {@link Closure#DELEGATE_FIRST} resolution strategy.
     *
     * @param dsl closure that writes XML markup
     * @return generated XML payload string
     */
    static String toXml(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> dsl) {
        def writer = new StringWriter()
        def markupBuilder = new MarkupBuilder(writer)
        def c = (Closure) dsl.clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = markupBuilder
        c.call()
        writer.toString()
    }
}
