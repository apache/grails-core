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
import org.gradle.tooling.ProjectConnection
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer


/**
 * Cached Gradle operation that reads a Map
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@InheritConstructors
@CompileStatic
abstract class MapReadingCachedGradleOperation <V> extends CachedGradleOperation<Map<String, V>> {
    @Override
    Map<String, V> readFromCached(File f) {
        def map = (Map<String, Object>) f.withReader { BufferedReader r ->
            new Yaml(new SafeConstructor(new LoaderOptions())).load(r)
        }
        Map<String, V> newMap = [:]

        for(entry in map.entrySet()) {
            newMap.put(entry.key, createMapValue(entry.value))
        }
        return newMap
    }

    abstract V createMapValue(Object value)

    @Override
    void writeToCache(PrintWriter writer, Map<String, V> data) {
        def options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        Map toWrite = data.collectEntries { String key, V val ->
            if(val instanceof Iterable) {
                return [(key):val.collect() { it.toString() }]
            }
            else {
                return [(key):val.toString()]
            }
        }
        new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options).dump(toWrite, writer)
    }

}
