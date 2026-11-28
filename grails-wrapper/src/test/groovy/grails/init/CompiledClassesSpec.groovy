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
package grails.init

import java.nio.charset.StandardCharsets

import grails.proxy.SystemPropertiesAuthenticator
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The jar of the wrapper is committed into every project, so its classes are compiled to be small:
 * they keep the source file and the line numbers a stack trace shows, and leave out the tables of
 * local variables, which only a debugger reads.
 */
class CompiledClassesSpec extends Specification {

    @Unroll
    void "#type.simpleName keeps its source file and line numbers, and has no table of local variables"() {
        when: 'the names of the attributes a class file has are in its constant pool'
        String constants = new String(bytesOf(type), StandardCharsets.ISO_8859_1)

        then:
        constants.contains('SourceFile')
        constants.contains('LineNumberTable')
        !constants.contains('LocalVariableTable')
        !constants.contains('LocalVariableTypeTable')

        where:
        type << [GrailsUpdater, GrailsWrapper, GrailsWrapperRepo, StartClass, Start, SystemPropertiesAuthenticator]
    }

    void "a stack trace of the wrapper still names the line it comes from"() {
        when:
        new GrailsVersion('not a version')

        then:
        IllegalArgumentException e = thrown()
        e.stackTrace.find { StackTraceElement element -> element.className == GrailsVersion.name }?.lineNumber > 0
    }

    private static byte[] bytesOf(Class<?> type) {
        InputStream stream = type.getResourceAsStream("/${type.name.replace('.', '/')}.class")
        assert stream != null
        return stream.withCloseable { InputStream it -> it.readAllBytes() }
    }
}
