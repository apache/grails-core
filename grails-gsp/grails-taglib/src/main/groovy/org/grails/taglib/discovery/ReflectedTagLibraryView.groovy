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
package org.grails.taglib.discovery

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import groovy.transform.CompileStatic

/**
 * A tag library read from its compiled class, as an application reads one when it registers it.
 *
 * @since 8.0.0
 */
@CompileStatic
final class ReflectedTagLibraryView implements TagLibraryView {

    private final Class<?> type

    ReflectedTagLibraryView(Class<?> type) {
        this.type = type
    }

    @Override
    List<TagMethodView> declaredMethods() {
        List<TagMethodView> declared = new ArrayList<>()
        for (Method method in type.getDeclaredMethods()) {
            declared.add(new ReflectedTagMethodView(method))
        }
        return declared
    }

    @Override
    List<String> declaredClosureFieldNames() {
        List<String> names = new ArrayList<>()
        for (Field field in type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue
            }
            if (Closure.isAssignableFrom(field.getType())) {
                names.add(field.getName())
            }
        }
        return names
    }

    @Override
    TagLibraryView superclassView() {
        Class<?> superClass = type.getSuperclass()
        if (superClass == null || superClass == Object) {
            return null
        }
        return new ReflectedTagLibraryView(superClass)
    }
}
