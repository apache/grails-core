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

package org.grails.cli.gradle.cache

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

/**
 * A {@link CachedGradleOperation} that reads and writes a list of values
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@InheritConstructors
abstract class ListReadingCachedGradleOperation<T> extends CachedGradleOperation<List<T>>{

    @Override
    List<T> readFromCached(File f) {
        return f.text.split('\n').collect() { String str -> createListEntry(str) }
    }

    protected abstract T createListEntry(String str)

    @Override
    void writeToCache(PrintWriter writer, List<T> data) {
        for (url in data) writer.println(url.toString())
    }


}
