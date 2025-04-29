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
package org.grails.config

import groovy.transform.CompileStatic
import org.springframework.core.env.EnumerablePropertySource

/**
 * A {@link org.springframework.core.env.PropertySource} with keys prefixed by the given prefix
 *
 * @author Graeme Rocher
 * @since 3.0
 */

@CompileStatic
class PrefixedMapPropertySource extends EnumerablePropertySource {
    final EnumerablePropertySource source
    final String prefix
    final String[] propertyNames

    PrefixedMapPropertySource(String prefix, EnumerablePropertySource source) {
        super(prefix + "_" + source.getName())
        this.prefix = prefix
        this.source = source
        this.propertyNames = source.propertyNames.collect() { String n -> "${prefix}.$n".toString() } as String[]
    }

    @Override
    Object getProperty(String name) {
        return source.getProperty("${prefix}.$name")
    }
}
