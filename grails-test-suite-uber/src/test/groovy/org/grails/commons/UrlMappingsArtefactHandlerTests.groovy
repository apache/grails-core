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
package org.grails.commons

import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for UrlMappingsArtefactHandler.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
class UrlMappingsArtefactHandlerTests {

    def mappingScript = '''
mappings {
  "/$id/$year?/$month?/$day?" {
        controller = "blog"
        action = "show"
        constraints {
            year(matches:/\\d{4}/)
            month(matches:/\\d{2}/)
        }
  }

  "/product/$name" {
        controller = "product"
        action = "show"
  }
}
'''

    @Test
    void testUrlMappingsArtefactHandler() {
        def gcl = new GroovyClassLoader()
        Class mappings = gcl.parseClass(mappingScript, "MyUrlMappings")
        def handler = new UrlMappingsArtefactHandler()

        assertTrue handler.isArtefactClass(mappings)
        assertNotNull handler.newArtefactClass(mappings)
    }
}
