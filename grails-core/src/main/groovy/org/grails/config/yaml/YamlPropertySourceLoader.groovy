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
package org.grails.config.yaml

import java.util.stream.Collectors

import groovy.transform.CompileStatic
import org.springframework.beans.factory.config.YamlProcessor
import org.springframework.boot.env.PropertySourceLoader
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Profiles
import org.springframework.core.env.PropertySource
import org.springframework.core.io.Resource
import org.springframework.util.StringUtils

import grails.plugins.GrailsPlugin
import grails.util.Environment
import org.grails.config.NavigableMap
import org.grails.config.NavigableMapPropertySource

/**
 * Replacement for Spring Boot's YAML loader that uses Grails' NavigableMap.
 *
 * @author graemerocher
 * @since 3.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@CompileStatic
class YamlPropertySourceLoader extends YamlProcessor implements PropertySourceLoader {

    private static final String PROFILE_SELECTOR = 'spring.config.activate.on-profile'
    private static final String LEGACY_PROFILE_SELECTOR = 'spring.profiles'

    @Override
    String[] getFileExtensions() {
        return ['yml', 'yaml'] as String[]
    }

    @Override
    List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        return load(name, resource, Collections.emptyList())
    }

    List<PropertySource<?>> load(String name, Resource resource, List<String> filteredKeys) throws IOException {
        setResources(resource)
        // Select source documents once; merging resolved configuration must not re-evaluate JVM profiles.
        final List<String> activeProfiles = Arrays.asList(
                StringUtils.tokenizeToStringArray(System.getProperty('spring.profiles.active', ''), ','))
        setDocumentMatchers((DocumentMatcher) (properties -> {
            final String[] profiles = profileSelectors(properties, PROFILE_SELECTOR)
            final String[] legacyProfiles = profileSelectors(properties, LEGACY_PROFILE_SELECTOR)
            final boolean matchesProfile = profiles.length == 0 ||
                    Profiles.of(profiles).matches(candidate -> activeProfiles.contains(candidate))
            final boolean matchesLegacyProfile = legacyProfiles.length == 0 ||
                    Profiles.of(legacyProfiles).matches(candidate -> activeProfiles.stream().anyMatch(active -> candidate.equalsIgnoreCase(active)))
            return matchesProfile && matchesLegacyProfile ? MatchStatus.FOUND : MatchStatus.NOT_FOUND
        }))
        List<Map<String, Object>> loaded = load()
        if (loaded.isEmpty()) {
            return Collections.emptyList()
        }
        List<PropertySource<?>> propertySources = new ArrayList<>(loaded.size())
        NavigableMap propertySource = new NavigableMap()
        //Now merge the environment config over the top of the normal stuff
        loaded.forEach(map -> {
            final Environment env = Environment.getCurrentEnvironment()
            String currentEnvironment = env != null ? env.getName() : null
            if (currentEnvironment != null) {
                final String prefix = GrailsPlugin.ENVIRONMENTS + '.' + currentEnvironment + '.'
                final Set<String> environmentSpecificEntries =
                        map.keySet().stream().filter(k -> k.startsWith(prefix)).collect(Collectors.toSet())

                for (String entry in environmentSpecificEntries) {
                    map.put(entry.substring(prefix.length()), map.get(entry))
                }
            }
            if (filteredKeys != null) {
                for (String filteredKey in filteredKeys) {
                    map.remove(filteredKey)
                }
            }
            // Spring Boot would evaluate these again against its own active profiles and drop the merged source.
            map.keySet().removeIf(key -> isProfileSelectorKey(key))
            propertySource.merge(map, true)
        })
        propertySources.add(
                new NavigableMapPropertySource(name, propertySource))

        return propertySources
    }

    /**
     * Collects the non-blank selector values for the given key. A scalar selector is read from the key itself and
     * split on commas, while a YAML sequence is flattened to indexed keys ({@code key[0]}, {@code key[1]}, ...).
     * Either way the values are alternatives, matching how Spring Boot binds {@code spring.config.activate.on-profile}
     * to a {@code String[]}.
     */
    private static String[] profileSelectors(Properties properties, String key) {
        final List<String> selectors = new ArrayList<>()
        for (String selector in StringUtils.commaDelimitedListToStringArray(properties.getProperty(key))) {
            addProfileSelector(selectors, selector)
        }
        for (Object name in properties.keySet()) {
            if (name instanceof String && isIndexedKey((String) name, key)) {
                addProfileSelector(selectors, properties.getProperty((String) name))
            }
        }
        return selectors.toArray(new String[0])
    }

    private static void addProfileSelector(List<String> selectors, String selector) {
        if (selector != null && !selector.trim().isEmpty()) {
            selectors.add(selector.trim())
        }
    }

    private static boolean isProfileSelectorKey(String name) {
        return name == PROFILE_SELECTOR || isIndexedKey(name, PROFILE_SELECTOR) ||
                name == LEGACY_PROFILE_SELECTOR || isIndexedKey(name, LEGACY_PROFILE_SELECTOR)
    }

    private static boolean isIndexedKey(String name, String key) {
        final int start = key.length() + 1
        final int end = name.length() - 1
        if (end <= start || !name.startsWith(key + '[') || name.charAt(end) != (char) ']') {
            return false
        }
        for (int i = start; i < end; i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false
            }
        }
        return true
    }

    List<Map<String, Object>> load() {
        final List<Map<String, Object>> result = new ArrayList<>()
        process((properties, map) -> result.add(getFlattenedMap(map)))
        return result
    }

}
