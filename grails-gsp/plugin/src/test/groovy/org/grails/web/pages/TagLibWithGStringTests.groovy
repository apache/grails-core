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
package org.grails.web.pages

import org.grails.taglib.GrailsTagException
import org.grails.web.errors.GrailsExceptionResolver
import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TagLibWithGStringTests extends AbstractGrailsTagTests {

    @BeforeEach
    protected void onSetUp() {
        
        gcl.parseClass('''
class GroovyStringTagLib {

   static namespace = 'jeff'

   Closure doit = { attrs ->
       out << "some foo ${fooo}"
   }
}
''')
    }

    @Test
    void testMissingPropertyGString() {
        def template = '<jeff:doit />'

        try {
            applyTemplate(template)
        }
        catch (GrailsTagException e) {
            def cause = GrailsExceptionResolver.getRootCause(e)
            assertTrue cause instanceof MissingPropertyException, "The cause should have been a MPE but was ${cause}"
            assertEquals 1,e.lineNumber
        }
    }
}
