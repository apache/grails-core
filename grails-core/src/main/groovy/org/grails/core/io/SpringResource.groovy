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
import groovy.transform.PackageScope
import org.grails.io.support.Resource

/**
 * Bridges Grails and Spring Resource APIs
 *
 * @author Graeme Rocher
 * @since 2.2
 */
@CompileStatic
class SpringResource implements Resource {

    @PackageScope
    org.springframework.core.io.Resource springResource

    SpringResource(org.springframework.core.io.Resource springResource) {
        this.springResource = springResource
    }

    InputStream getInputStream() throws IOException {
        return springResource.getInputStream()
    }

    boolean exists() {
        return springResource.exists()
    }

    boolean isReadable() {
        return springResource.isReadable()
    }

    URL getURL() throws IOException {
        return springResource.getURL()
    }

    URI getURI() throws IOException {
        return springResource.getURI()
    }

    File getFile() throws IOException {
        return springResource.getFile()
    }

    long contentLength() throws IOException {
        return springResource.contentLength()
    }

    long lastModified() throws IOException {
        return springResource.lastModified()
    }

    String getFilename() {
        return springResource.getFilename()
    }

    String getDescription() {
        return springResource.getDescription()
    }

    Resource createRelative(String relativePath) {
        try {
            return new SpringResource(springResource.createRelative(relativePath))
        }
        catch (IOException e) {
            return null
        }
    }

}
