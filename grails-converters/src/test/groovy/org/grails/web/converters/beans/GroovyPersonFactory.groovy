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
package org.grails.web.converters.beans

import groovy.transform.CompileStatic

/**
 * Hands out instances of Groovy classes that are not public, but whose read methods are. This is the
 * shape produced by an anonymous implementation of a public interface inside a Grails controller.
 */
@CompileStatic
class GroovyPersonFactory {

    /**
     * @return an anonymous implementation of a public interface
     */
    static PublicPerson anonymousPerson(String name, int age) {
        new PublicPerson() {

            @Override
            String getName() {
                name
            }

            @Override
            int getAge() {
                age
            }
        }
    }

    /**
     * @return an anonymous implementation that also exposes a public field
     */
    static PublicPerson anonymousPersonWithPublicField(String name, int age, String nick) {
        new PublicPerson() {

            public String nickname = nick

            @Override
            String getName() {
                name
            }

            @Override
            int getAge() {
                age
            }
        }
    }

    /**
     * @return an instance of a package-private class implementing a public interface
     */
    static PublicPerson packagePrivatePerson(String name, int age) {
        new PackagePrivateGroovyPerson(name, age)
    }

    /**
     * @return an instance of a package-private class that implements no interface at all
     */
    static Object standalonePerson(String name) {
        new PackagePrivateStandaloneGroovyBean(name)
    }
}
