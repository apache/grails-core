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
package org.grails.web.databinding.bindingsource

import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.Node
import groovy.xml.slurpersupport.NodeChild

import org.grails.databinding.xml.GPathResultMap

/**
 * @author Jeff Brown
 * @since 2.3
 */
class HalGPathResultMap extends GPathResultMap {

    HalGPathResultMap(GPathResult gpath) {
        super(gpath)
    }

    Object get(key) {
        def resourceElements = this.@gpath['resource']
        if(resourceElements.size() > 0) {
            def match = resourceElements.iterator().findAll {
                it.@rel.text() == key
            }
            if(match) {
                if(match.size() == 1) {
                    return new HalGPathResultMap(match[0])
                }
                if(match.size() > 1) {
                    def list = []
                    match.each {
                        list << new HalGPathResultMap(it)
                    }
                    return list
                }
            }
        }
        return super.get(key)
    }

    protected String getPropertyNameForNodeChild(NodeChild child) {
        def propertyName = child.name()
        if(propertyName == 'resource') {
            propertyName = child.attributes().get('rel')
        }
        propertyName
    }

    protected String getPropertyNameForNode(Node node) {
        def propertyName = node.name()
        if(propertyName == 'resource') {
            propertyName = node.attributes().get('rel')
        }
        propertyName
    }
}
