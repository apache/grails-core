/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.core.io;

import java.io.IOException;
import java.net.URL;

import org.springframework.core.io.ByteArrayResource;

/**
 * Hacky version of the ByteArrayResource that implements the {@link
 * #getURL()} method, required for the resource to work with Spring's
 * ServletContextResource.
 *
 * @author pledbrook
 */
public class GrailsByteArrayResource extends ByteArrayResource {

    public GrailsByteArrayResource(byte[] byteArray) {
        super(byteArray);
    }

    public GrailsByteArrayResource(byte[] byteArray, String location) {
        super(byteArray, location);
    }

    /**
     * Overrides the default behaviour to generate a fake "file:" URL
     * so that the resource can be used from a ServletContextResource.
     */
    @Override
    public URL getURL() throws IOException {
        return new URL("file", null, getDescription());
    }

    @Override
    public String getFilename() throws IllegalStateException {
        return super.getDescription();
    }
}
