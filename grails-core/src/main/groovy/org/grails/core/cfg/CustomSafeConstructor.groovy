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
package org.grails.core.cfg

import groovy.transform.Internal
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.Construct
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag

/**
 * Yaml constructor to create containers with sensible
 * default array bounds.
 *
 * @author James Kleeh
 * @since 6.0.0
 */
@Internal
class CustomSafeConstructor extends SafeConstructor {

    CustomSafeConstructor() {
        super(new LoaderOptions())

        // Use a custom construct for treating ISO timestamps as strings
        yamlConstructors.put(Tag.TIMESTAMP, new ConstructIsoTimestampString())
    }

    @Override
    protected Map<Object, Object> newMap(MappingNode node) {
        return createDefaultMap(node.getValue().size())
    }

    @Override
    protected List<Object> newList(SequenceNode node) {
        return createDefaultList(node.getValue().size())
    }

    static class ConstructIsoTimestampString implements Construct {

        @Override
        Object construct(Node node) {
            return SafeConstructor.constructScalar(node) as String
        }

        @Override
        void construct2ndStep(Node node, Object data) {}
    }
}