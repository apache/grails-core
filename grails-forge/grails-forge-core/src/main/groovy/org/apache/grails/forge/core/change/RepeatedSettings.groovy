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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag
import java.nio.charset.StandardCharsets

/**
 * The settings a YAML configuration file has more than once in one of its documents, as YAML and
 * Grails read the file and not by the letters of its lines. A key is what the YAML reader makes of
 * it: {@code 'a.b'} and {@code "a.b"} in quotes, a key with an escaped character and a key that is
 * written after {@code ?} are the key {@code a.b}. A setting is the keys on the way to a value with
 * dots between them, which is the name Grails has for it: {@code a.b: 1} and {@code a:} with
 * {@code b: 1} below it are the setting {@code a.b}.
 *
 * <p>Two things count:</p>
 * <ul>
 * <li>a key that one mapping has twice, at whatever depth. A YAML reader refuses such a file, or
 * takes one of the two;</li>
 * <li>a setting that has a value in one place and is there again in another under other keys, with
 * a value or as a group of settings. No YAML reader objects, and which of the two counts is decided
 * by the order Grails reads them in.</li>
 * </ul>
 *
 * <p>A group of settings that is written in two ways, as {@code a.b:} with {@code c: 1} below it and
 * {@code a:}, {@code b:} with {@code d: 2} below them, is no repetition: its settings are others.
 * The maps of a list are not looked into, since a list is one value, and neither is a key that is
 * no scalar or the merge key {@code <<}.</p>
 *
 * @since 9.0
 */
@CompileStatic
@PackageScope
final class RepeatedSettings {

    private static final int MAX_NODES = 200000
    private static final int MAX_DEPTH = 64
    private static final Integer VALUE = 1
    private static final Integer GROUP = 2

    /** The keys the top of a document has twice, in the order of the file */
    final List<String> topLevelKeys

    /** Every other setting that is there twice, by its name with dots, in the order of the file */
    final List<String> settings

    private int nodes

    private RepeatedSettings() {
        this.topLevelKeys = []
        this.settings = []
    }

    /**
     * @param yaml the content of a YAML file
     * @return what the file has more than once, or {@code null} when it is not YAML that reads
     */
    static RepeatedSettings of(byte[] yaml) {
        RepeatedSettings repeated = new RepeatedSettings()
        try {
            Yaml reader = new Yaml(new SafeConstructor(new LoaderOptions()))
            for (Node document : reader.composeAll(new StringReader(new String(yaml, StandardCharsets.UTF_8)))) {
                if (document instanceof MappingNode) {
                    repeated.walk((MappingNode) document, '', new HashMap<String, Integer>(), 0)
                }
            }
        } catch (YAMLException | StackOverflowError ignored) {
            return null
        }
        return repeated
    }

    /**
     * @param others what other versions of the file have more than once
     * @return the keys of {@link #topLevelKeys} that none of the others has twice
     */
    List<String> topLevelKeysBeyond(RepeatedSettings... others) {
        List<String> beyond = new ArrayList<String>(topLevelKeys)
        for (RepeatedSettings other : others) {
            beyond.removeAll(other.topLevelKeys)
        }
        return beyond
    }

    /**
     * @param others what other versions of the file have more than once
     * @return the settings of {@link #settings} that none of the others has twice
     */
    List<String> settingsBeyond(RepeatedSettings... others) {
        List<String> beyond = new ArrayList<String>(settings)
        for (RepeatedSettings other : others) {
            beyond.removeAll(other.settings)
        }
        return beyond
    }

    // seen: the settings of the document so far, each as a value or as a group of settings
    private void walk(MappingNode mapping, String prefix, Map<String, Integer> seen, int depth) {
        if (depth > MAX_DEPTH) {
            throw new YAMLException('The mappings are nested too deeply')
        }
        Set<String> keys = new HashSet<>()
        for (NodeTuple tuple : mapping.getValue()) {
            if (++nodes > MAX_NODES) {
                throw new YAMLException('The file has too many keys')
            }
            Node keyNode = tuple.getKeyNode()
            if (!(keyNode instanceof ScalarNode) || keyNode.getTag() == Tag.MERGE) {
                continue
            }
            String key = ((ScalarNode) keyNode).getValue()
            String setting = prefix.isEmpty() ? key : prefix + '.' + key
            Node value = tuple.getValueNode()
            boolean group = value instanceof MappingNode
            Integer before = seen.get(setting)
            if (!keys.add(key)) {
                found(prefix.isEmpty() ? topLevelKeys : settings, setting)
            } else if (before != null && (before == VALUE || !group)) {
                found(settings, setting)
            }
            seen.put(setting, group && before != VALUE ? GROUP : VALUE)
            if (group) {
                walk((MappingNode) value, setting, seen, depth + 1)
            }
        }
    }

    private static void found(List<String> into, String setting) {
        if (!into.contains(setting)) {
            into << setting
        }
    }
}
