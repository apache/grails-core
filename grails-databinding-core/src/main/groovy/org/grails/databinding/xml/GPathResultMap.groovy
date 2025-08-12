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
package org.grails.databinding.xml

import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.Node
import groovy.xml.slurpersupport.NodeChild

/**
 * @author Jeff Brown
 * @since 2.3
 */
class GPathResultMap implements Map {

    protected GPathResult gpath
    protected id

    GPathResultMap(GPathResult gpath) {
        this.gpath = gpath
        this.@id = gpath.@id.text() ?: null
    }

    int size() {
        def uniqueNames = [] as Set
        gpath.children().each { child ->
            uniqueNames << getPropertyNameForNodeChild(child)
        }
        uniqueNames.size()
    }

    boolean containsKey(key) {
        if (key == 'id') {
            return this.@id != null || gpath['id'].size()
        }
        gpath[key].size()
    }

    Set entrySet() {
        def entries = [] as Set
        def uniqueChildNames = [] as Set
        gpath.childNodes().each { childNode ->
            uniqueChildNames << getPropertyNameForNode(childNode)
        }
        uniqueChildNames.each { name ->
            def value = get name
            entries << new AbstractMap.SimpleImmutableEntry(name, value)
        }
        if (this.@id != null) {
            entries << new AbstractMap.SimpleImmutableEntry('id', this.@id)
        }
        return entries
    }

    Object get(key) {
        if ('id' == key && this.@id) {
            return this.@id
        }

        def value = gpath.children().findAll { it.name() == key }
        if (value.size() == 0) {
            return null
        }
        if (value.size() > 1) {
            def list = []
            value.iterator().each {
                def theId = it.@id.text()
                if (!''.equals(theId)) {
                    def theMap = new GPathResultMap(it)
                    list << theMap
                } else {
                    if (it.children().size() > 0) {
                        def theMap = new GPathResultMap(it)
                        list << theMap
                    } else {
                        list << it.text()
                    }
                }
            }
            return list
        }
        if (value.children().size() == 0) {
            if (value.@id.text()) {
                return [id: value.@id.text()]
            }
            return value.text()
        }
        new GPathResultMap(value)
    }

    Set keySet() {
        def keys = gpath.children().collect {
            getPropertyNameForNodeChild it
        } as Set
        if (this.@id != null) {
            keys << 'id'
        }
        keys
    }

    protected String getPropertyNameForNodeChild(NodeChild child) {
        child.name()
    }

    protected String getPropertyNameForNode(Node node) {
        node.name()
    }

    void clear() {
        throw new UnsupportedOperationException()
    }

    boolean containsValue(value) {
        throw new UnsupportedOperationException()
    }

    boolean isEmpty() {
        !size()
    }

    Object put(key, value) {
        throw new UnsupportedOperationException()
    }

    void putAll(Map m) {
        throw new UnsupportedOperationException()
    }

    Object remove(key) {
        throw new UnsupportedOperationException()
    }

    Collection values() {
        throw new UnsupportedOperationException()
    }
}
