/* Copyright 2004-2024 the original author or authors.
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
package org.grails.support;

import org.grails.core.io.StaticResourceLoader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the StaticResourceLoader class
 *
 * @author Graeme Rocher
 * @since 0.4
 *        <p>
 *        Created: Feb 26, 2007
 *        Time: 3:49:27 PM
 */
public class StaticResourceLoaderTests {

    @Test
    public void testGetResource() throws Exception {
        StaticResourceLoader srl = new StaticResourceLoader();
        srl.setBaseResource(new UrlResource("http://grails.org/"));

        Resource r = srl.getResource("/Home");
        assertEquals("http://grails.org/Home", r.getURL().toString());
    }

    @Test
    public void testIllegalState() {
        StaticResourceLoader srl = new StaticResourceLoader();

        try {
            srl.getResource("/foo");
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ise) {
            // expected
        }
    }
}
