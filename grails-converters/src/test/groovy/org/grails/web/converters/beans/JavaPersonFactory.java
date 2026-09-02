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
package org.grails.web.converters.beans;

/**
 * Hands out instances of Java classes that are not public, but whose read methods are.
 */
public final class JavaPersonFactory {

    private JavaPersonFactory() {
    }

    /**
     * @return an anonymous implementation of a public interface
     */
    public static PublicPerson anonymousPerson(final String name, final int age) {
        return new PublicPerson() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getAge() {
                return age;
            }
        };
    }

    /**
     * @return an instance of a package-private class implementing a public interface
     */
    public static PublicPerson packagePrivatePerson(String name, int age) {
        return new PackagePrivateJavaPerson(name, age);
    }

    /**
     * @return an instance of a package-private class that implements no interface at all, so the
     *         read method cannot be resolved to a public declaring type
     */
    public static Object standalonePerson(String name) {
        return new PackagePrivateStandaloneJavaBean(name);
    }
}
