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
package org.grails.forge.template

import groovy.transform.CompileStatic

/**
 * The order of the keys of a generated YAML configuration file. It is a function of the keys alone,
 * not of the order in which the features of an application put them there, so that a feature that is
 * added to an application puts its own keys into the file and leaves every other line where it was.
 *
 * <ul>
 * <li>At the top of a file, and at the top of each environment, the sections of {@link #SECTIONS}
 * come first, in that order, which is the order the file of an application without further features
 * has always had; every other key follows in alphabetical order.</li>
 * <li>Under {@code environments}, the environments of {@link #ENVIRONMENTS} come first, in that
 * order, and every other environment follows in alphabetical order.</li>
 * <li>The entries of a map whose order Grails reads as meaning, those of {@link #DECLARED}, are
 * in the order they were declared in: the order one feature has them in, and the entries of a
 * feature that is applied later after those of an earlier one.</li>
 * <li>Everywhere else the keys are in alphabetical order.</li>
 * </ul>
 *
 * <p>The exception is there because the order of those maps is not layout. Grails takes the first
 * entry of {@code grails.mime.types} for the format of a request that says nothing of its content,
 * and for a response to a request that accepts anything when {@code all} is not among the types;
 * the first matching pattern of {@code grails.cors.mappings} is the one that counts. Sorted, such a
 * map would mean something else than the feature wrote. A feature that is added still leaves every
 * other line where it was: its entries arrive among those that are there, which keep their order.</p>
 *
 * <p>Alphabetical is without regard to case, and by case for keys that are otherwise the same. The
 * elements of a list keep their order, and the keys of a map that is in a list are ordered
 * alphabetically.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class YamlKeyOrder {

    /** The sections that begin a file, and an environment, in their order. */
    public static final List<String> SECTIONS = ['info', 'dataSource', 'environments', 'hibernate', 'grails'].asImmutable()

    /** The environments that come first under {@code environments}, in their order. */
    public static final List<String> ENVIRONMENTS = ['development', 'test', 'production'].asImmutable()

    /**
     * The settings whose value is a map that Grails reads in its order, by their names with dots, at
     * the top of a file and in an environment: the entries of such a map keep the order they were
     * declared in. What is below an entry is in alphabetical order again.
     */
    public static final Set<String> DECLARED = ['grails.mime.types', 'grails.cors.mappings'].toSet().asImmutable()

    private static final String ENVIRONMENTS_KEY = 'environments'

    private YamlKeyOrder() {
    }

    /**
     * @param config the content of a configuration file
     * @return the same content with its keys in the order of the generator, at every depth. The map
     * that is given is not changed
     */
    static Map<String, Object> canonical(Map<?, ?> config) {
        return sections(config)
    }

    /**
     * Puts the keys of a configuration into the order another has them in, so that the two can be
     * compared line by line: a key both have is where the other has it, and a key only this one has
     * follows the key it followed, or, when it was the first, comes before the key it came before.
     * A configuration whose keys are in the order of the other already comes back as it is.
     *
     * @param config the content of a configuration file
     * @param other the content whose order is taken
     * @return the content of {@code config} in the order of {@code other}, at every depth of maps.
     * Neither map is changed
     */
    static Map<String, Object> following(Map<?, ?> config, Map<?, ?> other) {
        List<Object> keys = new ArrayList<Object>(config.keySet())
        List<Object> order = new ArrayList<Object>(other.keySet())
        List<Object> placed = keys.findAll { Object key -> other.containsKey(key) }.sort(false) { Object key -> order.indexOf(key) }
        for (int index = 0; index < keys.size(); index++) {
            Object key = keys[index]
            if (other.containsKey(key)) {
                continue
            }
            if (index > 0) {
                placed.add(placed.indexOf(keys[index - 1]) + 1, key)
                continue
            }
            Object next = keys.find { Object each -> other.containsKey(each) }
            placed.add(next == null ? placed.size() : placed.indexOf(next), key)
        }
        Map<Object, Object> result = new LinkedHashMap<>()
        for (Object key : placed) {
            Object value = config.get(key)
            Object there = other.get(key)
            result.put(key, value instanceof Map && there instanceof Map ? following((Map<?, ?>) value, (Map<?, ?>) there) : value)
        }
        return (Map<String, Object>) (Map) result
    }

    // the top of a file, and of an environment
    private static Map<String, Object> sections(Map<?, ?> map) {
        Map<Object, Object> result = new LinkedHashMap<>()
        for (Object key : sorted(map.keySet(), SECTIONS)) {
            Object value = map.get(key)
            if (String.valueOf(key) == ENVIRONMENTS_KEY && value instanceof Map) {
                Map<?, ?> environments = (Map<?, ?>) value
                Map<Object, Object> ordered = new LinkedHashMap<>()
                for (Object environment : sorted(environments.keySet(), ENVIRONMENTS)) {
                    Object of = environments.get(environment)
                    ordered.put(environment, of instanceof Map ? sections((Map<?, ?>) of) : alphabetical(of, null))
                }
                result.put(key, ordered)
            } else {
                result.put(key, alphabetical(value, String.valueOf(key)))
            }
        }
        return (Map<String, Object>) (Map) result
    }

    // setting: the name of the setting the value is of, with dots, or null in a list, where no setting has a name
    private static Object alphabetical(Object value, String setting) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value
            Map<Object, Object> result = new LinkedHashMap<>()
            Collection<?> keys = setting != null && DECLARED.contains(setting) ? map.keySet() : sorted(map.keySet(), Collections.<String>emptyList())
            for (Object key : keys) {
                result.put(key, alphabetical(map.get(key), setting == null ? null : setting + '.' + key))
            }
            return result
        }
        if (value instanceof List) {
            return ((List<?>) value).collect { Object each -> alphabetical(each, null) }
        }
        return value
    }

    // the keys that are named first, in the order they are named in, then the others. A key that is
    // written with dots, as a.b.c, is where its first step would be
    private static List<Object> sorted(Collection<?> keys, List<String> first) {
        return new ArrayList<Object>(keys).sort(false) { Object one, Object other ->
            String a = String.valueOf(one)
            String b = String.valueOf(other)
            int rankA = rank(a, first)
            int rankB = rank(b, first)
            if (rankA != rankB) {
                return rankA <=> rankB
            }
            int ignoringCase = String.CASE_INSENSITIVE_ORDER.compare(a, b)
            return ignoringCase != 0 ? ignoringCase : a <=> b
        }
    }

    private static int rank(String key, List<String> first) {
        int dot = key.indexOf('.')
        int index = first.indexOf(dot < 0 ? key : key.substring(0, dot))
        return index < 0 ? first.size() : index
    }
}
