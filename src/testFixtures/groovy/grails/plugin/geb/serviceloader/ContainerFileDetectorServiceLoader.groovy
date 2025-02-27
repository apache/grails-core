/*
 * Copyright 2025 original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.geb.serviceloader

import grails.plugin.geb.ContainerFileDetector
import grails.plugin.geb.DefaultContainerFileDetector
import groovy.transform.CompileStatic

/**
 * ServiceLoader (META-INF) for {@link ContainerFileDetector} that allows programmatic overwriting.
 *
 * @since 4.2
 */
@CompileStatic
class ContainerFileDetectorServiceLoader {

    private static ContainerFileDetector instance

    static ContainerFileDetector getInstance() {
        instance ?: ServiceLoader.load(ContainerFileDetector)
                .findFirst()
                .orElse(new DefaultContainerFileDetector())
    }

    static void setInstance(ContainerFileDetector instance) {
        this.instance = instance
    }

    /**
     * Class must have a zero-argument constructor (ServiceLoader Requirement).
     */
    static void setInstance(Class<? extends ContainerFileDetector> clazz) {
        setInstance(clazz.getDeclaredConstructor().newInstance())
    }
}