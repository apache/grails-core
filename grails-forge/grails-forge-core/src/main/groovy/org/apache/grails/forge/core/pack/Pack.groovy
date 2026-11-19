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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic

/**
 * A feature pack that was read: its descriptor, and the content of the files it adds to an application,
 * as it was when the pack was read.
 *
 * @since 9.0
 * @see PackLoader
 */
@CompileStatic
final class Pack {

    /** The name of the descriptor in a pack */
    public static final String DESCRIPTOR = 'feature.yml'

    /** The directory of a pack that holds the files its descriptor names */
    public static final String TEMPLATES = 'templates'

    private final PackDescriptor descriptor
    private final String origin
    private final Map<String, byte[]> files

    /**
     * @param descriptor the descriptor of the pack
     * @param origin where the pack came from, for messages
     * @param files the content of the file of each {@code from} of the descriptor
     */
    Pack(PackDescriptor descriptor, String origin, Map<String, byte[]> files) {
        this.descriptor = descriptor
        this.origin = origin
        Map<String, byte[]> copies = new LinkedHashMap<>()
        files.each { String from, byte[] content -> copies.put(from, content.clone()) }
        this.files = Collections.unmodifiableMap(copies)
    }

    PackDescriptor getDescriptor() {
        return descriptor
    }

    /**
     * @return where the pack came from, for messages
     */
    String getOrigin() {
        return origin
    }

    /**
     * @param from the {@code from} of a file of the descriptor
     * @return a copy of the content of the file it names
     */
    byte[] content(String from) {
        byte[] content = files.get(from)
        if (content == null) {
            throw new IllegalArgumentException("The feature pack ${origin} has no file ${from}")
        }
        return content.clone()
    }

    @Override
    String toString() {
        return "Pack(${descriptor.name} from ${origin})"
    }
}
