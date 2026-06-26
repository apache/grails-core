/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.grails.data.testing.tck.base

import spock.lang.Shared
import spock.lang.Specification
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class GrailsDataTckSpec<T extends GrailsDataTckManager> extends Specification {

    @Shared
    T manager

    void setupSpec() {
        ServiceLoader<GrailsDataTckManager> loader = ServiceLoader.load(GrailsDataTckManager)
        def providers = loader.stream().map { it.get() }.toList()

        // Try to find a manager that matches the generic type T
        Class<T> managerClass = findManagerClass()
        def preferred = providers.find { managerClass.isInstance(it) }

        manager = (preferred ?: providers ? providers.first() : loader.findFirst().get()) as T
        manager.setupSpec()
    }

    private Class<T> findManagerClass() {
        Class<?> clazz = getClass()
        while (clazz != Object) {
            Type superclass = clazz.getGenericSuperclass()
            if (superclass instanceof ParameterizedType) {

                ParameterizedType pt = (ParameterizedType) superclass
                if (pt.getRawType() == GrailsDataTckSpec) {

                    return (Class<T>) pt.getActualTypeArguments()[0]
                }
            }
            clazz = clazz.getSuperclass()
        }
        return (Class<T>) GrailsDataTckManager
    }

    void cleanupSpec() {
        manager.cleanupSpec()
    }

    void setup() {
        manager.setup(this.getClass())
    }

    void cleanup() {
        manager.cleanup()
    }
}
