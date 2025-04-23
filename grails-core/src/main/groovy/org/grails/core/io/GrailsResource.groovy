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

package org.grails.core.io

import groovy.transform.CompileStatic
import org.springframework.core.io.Resource

/**
 * Bridges Grails and Spring resource APIs
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsResource implements Resource{

    org.grails.io.support.Resource resource

    GrailsResource(org.grails.io.support.Resource resource) {
        this.resource = resource
    }

    @Override
    boolean exists() {
        resource.exists()
    }

    @Override
    boolean isReadable() {
        resource.readable
    }

    @Override
    boolean isOpen() {
        false
    }

    @Override
    URL getURL() throws IOException {
        resource.URL
    }

    @Override
    URI getURI() throws IOException {
        resource.URI
    }

    @Override
    File getFile() throws IOException {
        resource.file
    }

    @Override
    long contentLength() throws IOException {
        resource.contentLength()
    }

    @Override
    long lastModified() throws IOException {
        resource.lastModified()
    }

    @Override
    Resource createRelative(String relativePath) throws IOException {
        new GrailsResource(resource.createRelative(relativePath))
    }

    @Override
    String getFilename() {
        resource.filename
    }

    @Override
    String getDescription() {
        resource.description
    }

    @Override
    InputStream getInputStream() throws IOException {
        resource.inputStream
    }
}
