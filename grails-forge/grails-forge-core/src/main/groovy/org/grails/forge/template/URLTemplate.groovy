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
package org.grails.forge.template

import groovy.transform.CompileStatic

@CompileStatic
class URLTemplate implements Template {

    private final String path
    private final URL url
    private final boolean executable

    URLTemplate(String path, URL url) {
        this(path, url, false)
    }

    URLTemplate(String path, URL url, boolean executable) {
        this.path = path
        this.url = Objects.requireNonNull(url, 'Resource not found for url: ' + url)
        this.executable = executable
    }

    @Override
    void write(OutputStream outputStream) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            byte[] buffer = new byte[1024]
            int len
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len)
            }
        }
    }

    boolean isExecutable() {
        return executable
    }

    @Override
    String getPath() {
        return path
    }
}
