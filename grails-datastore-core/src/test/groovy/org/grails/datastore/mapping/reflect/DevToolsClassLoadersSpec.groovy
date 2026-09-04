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
package org.grails.datastore.mapping.reflect

import spock.lang.Specification

class DevToolsClassLoadersSpec extends Specification {

    ClassLoader originalContextClassLoader

    def setup() {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
    }

    void "isRestartClassLoader is false for null and ordinary loaders"() {
        expect:
        !DevToolsClassLoaders.isRestartClassLoader(null)
        !DevToolsClassLoaders.isRestartClassLoader(DevToolsClassLoaders.classLoader)
    }

    void "isRestartClassLoader matches the RestartClassLoader simple name"() {
        given:
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader

        expect:
        DevToolsClassLoaders.isRestartClassLoader(restartLoader)
    }

    void "isRestartClassLoader does not match RestartClassLoader ignoring case"() {
        given:
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class restartclassloader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader

        expect:
        !DevToolsClassLoaders.isRestartClassLoader(restartLoader)
    }

    void "isRestartClassLoader matches the Spring Boot RestartClassLoader FQCN"() {
        given:
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'package org.springframework.boot.devtools.restart.classloader\nclass RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader

        expect:
        DevToolsClassLoaders.isRestartClassLoader(restartLoader)
    }

    void "preferRestartClassLoader prefers a RestartClassLoader thread context class loader"() {
        given:
        ClassLoader fallback = new URLClassLoader([] as URL[], DevToolsClassLoaders.classLoader)
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        Thread.currentThread().contextClassLoader = restartLoader

        expect:
        DevToolsClassLoaders.preferRestartClassLoader(fallback).is(restartLoader)
    }

    void "preferRestartClassLoader returns the fallback when DevTools is not active"() {
        given:
        ClassLoader fallback = new URLClassLoader([] as URL[], DevToolsClassLoaders.classLoader)

        expect:
        DevToolsClassLoaders.preferRestartClassLoader(fallback).is(fallback)
    }

    void "preferRestartClassLoader ignores a non-restart thread context class loader"() {
        given:
        ClassLoader fallback = new URLClassLoader([] as URL[], DevToolsClassLoaders.classLoader)
        ClassLoader otherLoader = new URLClassLoader([] as URL[], DevToolsClassLoaders.classLoader)
        Thread.currentThread().contextClassLoader = otherLoader

        expect:
        DevToolsClassLoaders.preferRestartClassLoader(fallback).is(fallback)
    }

    void "preferRestartClassLoader prefers a RestartClassLoader even when fallback is null"() {
        given:
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        Thread.currentThread().contextClassLoader = restartLoader

        expect:
        DevToolsClassLoaders.preferRestartClassLoader(null).is(restartLoader)
    }

    void "preferRestartClassLoader falls back to this class's loader when fallback is null"() {
        expect:
        DevToolsClassLoaders.preferRestartClassLoader(null).is(DevToolsClassLoaders.classLoader)
    }

    void "preferRestartClassLoader returns a descendant fallback of the RestartClassLoader"() {
        given:
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        ClassLoader fallback = new URLClassLoader([] as URL[], restartLoader)
        Thread.currentThread().contextClassLoader = restartLoader

        expect:
        DevToolsClassLoaders.preferRestartClassLoader(fallback).is(fallback)
    }

    void "preferRestartClassLoader returns the RestartClassLoader fallback"() {
        given:
        ClassLoader restartLoader = new GroovyClassLoader().parseClass(
                'class RestartClassLoader extends ClassLoader {}'
        ).getDeclaredConstructor().newInstance() as ClassLoader
        Thread.currentThread().contextClassLoader = restartLoader

        expect:
        DevToolsClassLoaders.preferRestartClassLoader(restartLoader).is(restartLoader)
    }

    void "resolve delegates to preferRestartClassLoader"() {
        expect:
        DevToolsClassLoaders.resolve(null).is(DevToolsClassLoaders.preferRestartClassLoader(null))
    }
}
