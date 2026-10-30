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

import org.springframework.core.Ordered

import grails.async.web.StreamItemRenderer

/**
 * Renders text, numbers, booleans, characters and enum constants as their string form, and leaves every
 * other item to the renderers after it.
 *
 * @since 8.1
 */
@CompileStatic
class DefaultStreamItemRenderer implements StreamItemRenderer, Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100

    @Override
    String render(Object item) {
        if (item == null) {
            return ''
        }
        if (item instanceof CharSequence || item instanceof Number || item instanceof Boolean ||
                item instanceof Character || item instanceof Enum) {
            return item.toString()
        }
        return null
    }

    @Override
    int getOrder() {
        ORDER
    }

}
