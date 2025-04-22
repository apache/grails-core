/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.config.yaml;


import grails.plugins.GrailsPlugin;
import grails.util.Environment;
import org.grails.config.NavigableMap;
import org.grails.config.NavigableMapPropertySource;
import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replacement for Spring Boot's YAML loader that uses Grails' NavigableMap.
 *
 * @author graemerocher
 * @since 3.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class YamlPropertySourceLoader extends YamlProcessor implements PropertySourceLoader {
    @Override
    public String[] getFileExtensions() {
        return new String[] { "yml", "yaml" };
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        return load(name, resource, Collections.emptyList());
    }

    public List<PropertySource<?>> load(String name, Resource resource, List<String> filteredKeys) throws IOException {
        setResources(resource);
        setDocumentMatchers((DocumentMatcher) properties -> {
            final String profile = properties.getProperty("spring.profiles");
            return profile == null || profile.equalsIgnoreCase(System.getProperty("spring.profiles.active")) ? MatchStatus.FOUND : MatchStatus.NOT_FOUND;
        });
        List<Map<String, Object>> loaded = load();
        if (loaded.isEmpty()) {
            return Collections.emptyList();
        }
        List<PropertySource<?>> propertySources = new ArrayList<>(loaded.size());
        NavigableMap propertySource = new NavigableMap();
        //Now merge the environment config over the top of the normal stuff
        loaded.forEach(map -> {
            final Environment env = Environment.getCurrentEnvironment();
            String currentEnvironment = env != null ? env.getName() : null;
            if (currentEnvironment != null) {
                final String prefix = GrailsPlugin.ENVIRONMENTS + "." + currentEnvironment + ".";
                final Set<String> environmentSpecificEntries =
                        map.keySet().stream().filter(k -> k.startsWith(prefix)).collect(Collectors.toSet());

                for (String entry : environmentSpecificEntries) {
                    map.put(entry.substring(prefix.length()), map.get(entry));
                }
            }
            if (filteredKeys != null) {
                for (String filteredKey : filteredKeys) {
                    map.remove(filteredKey);
                }
            }
            propertySource.merge(map, true);
        });
        propertySources.add(
                new NavigableMapPropertySource(name ,propertySource));

        return propertySources;
    }


    public List<Map<String, Object>> load() {
        final List<Map<String, Object>> result = new ArrayList<>();
        process((properties, map) -> result.add(getFlattenedMap(map)));
        return result;
    }
}
