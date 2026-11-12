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
package functionaltests

import spock.lang.Specification

/**
 * A tag call in a controller declared by convention is compiled into a direct invocation.
 *
 * <p>Everything else that asserts this compiles a source in isolation, which proves the transform
 * works but not that a real project reaches it: the index has to be generated, packaged, placed on
 * the compile classpath and read, and the transform has to run after the trait that makes the class
 * able to call tags has been applied. This reads the class file this project actually produced.
 *
 * <p>This is where the ordering was caught. The rewrite ran in the same phase as the transforms that
 * inject artefact traits, ordered against them by priority, which holds only for transforms that
 * declare one: a trait arriving from a local transform is applied after every global transform has
 * run, so the class was read before it carried the trait. Which side of that a given class landed on
 * varied, and this spec was what reported it -- passing on macOS while failing on Linux -- because a
 * whole build is the only place the convention path is driven the way an application drives it.
 */
class CompiledTagCallSpec extends Specification {

    void 'a namespaced tag call in a convention controller is compiled into an invocation'() {
        given:
        byte[] compiled = classBytes(IncludesController)

        expect: 'the probe method is the one carrying the call'
        asText(compiled).contains('compiledTagCallProbe')

        and: 'and it reaches the tag through the invocation entry point rather than dynamically'
        asText(compiled).contains('org/grails/taglib/CompiledTagInvocation')
    }

    void 'a class that cannot call tags is left alone'() {
        expect: 'so the assertion above is about tag calls, not about every class in the project'
        !asText(classBytes(Book)).contains('org/grails/taglib/CompiledTagInvocation')
    }

    private static String asText(byte[] bytes) {
        new String(bytes, 'ISO-8859-1')
    }

    private static byte[] classBytes(Class<?> type) {
        String resource = type.name.replace('.', '/') + '.class'
        InputStream stream = type.classLoader.getResourceAsStream(resource)
        assert stream != null, "no class file for ${type.name}"
        stream.withCloseable { it.bytes }
    }
}
