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
package org.apache.grails.gradle.model.cli

import groovy.transform.CompileStatic

/**
 * The options the elements of a class path declare for commands ({@link CommandOptions}), put
 * together for the catalog of the commands of a project: its member {@code commandOptions}.
 *
 * <p>An option is listed with where it comes from. A name two elements declare for one command is
 * not chosen between: neither option is listed, and both are named. The application is the
 * exception, as it is for its commands: what it declares holds, and a plugin that declares the same
 * name is named. Declarations that say the same of an option are one declaration, of the first
 * element that has it.</p>
 *
 * @since 9.0
 */
@CompileStatic
class CommandOptionCatalog {

    private final List<Listed> declared = []
    private final Set<String> diagnostics = new TreeSet<String>()

    /**
     * Reads the declarations of one element of the class path. Elements are added in the order of the class path.
     *
     * @param origin where the element comes from, as the catalog names it
     * @param bytes the content of its {@link CommandOptions#LOCATION}, or {@code null} when it could not be read within {@link CommandOptions#MAX_BYTES}
     */
    void add(String origin, byte[] bytes) {
        CommandOptions.Read read
        try {
            if (bytes == null) {
                throw new CommandOptions.Refused("have more than ${CommandOptions.MAX_BYTES} bytes or cannot be read")
            }
            read = CommandOptions.read(bytes)
        } catch (CommandOptions.Refused refused) {
            diagnostics << "The options ${origin} declares for commands are not used: its declarations (${CommandOptions.LOCATION}) ${refused.message}.".toString()
            return
        }
        for (Map.Entry<Integer, String> refused : read.refused) {
            diagnostics << "Option ${refused.key} that ${origin} declares for a command (${CommandOptions.LOCATION}) is not used: it ${refused.value}.".toString()
        }
        if (read.more > 0) {
            diagnostics << "${origin} declares more than ${CommandOptions.MAX_OPTIONS_PER_ORIGIN} options for commands; the last ${read.more} are not used.".toString()
        }
        for (CommandOptions.Declared option : read.options) {
            if (!declared.any { Listed known -> known.origin == origin && known.option.sameAs(option) }) {
                declared << new Listed(origin: origin, option: option)
            }
        }
    }

    /**
     * @param said what there is to say of the declarations, one line each, which is added to
     * @return the options the catalog lists, by command and name: each with {@code command}, {@code name},
     * {@code description}, {@code type}, {@code property}, {@code origin} and, for a switch, {@code value}
     */
    List<Map<String, Object>> entries(Set<String> said) {
        said.addAll(diagnostics)
        Map<String, List<Listed>> byName = new TreeMap<String, List<Listed>>()
        for (Listed each : declared) {
            byName.computeIfAbsent("${each.option.command} --${each.option.name}".toString()) { String key -> new ArrayList<Listed>() } << each
        }
        List<Map<String, Object>> entries = []
        int left = 0
        for (Map.Entry<String, List<Listed>> named : byName) {
            Listed chosen = chosen(named.key, named.value, said)
            if (chosen == null) {
                continue
            }
            if (entries.size() == CommandOptions.MAX_OPTIONS) {
                left++
                continue
            }
            Map<String, Object> entry = new LinkedHashMap<String, Object>()
            entry.command = chosen.option.command
            entry.name = chosen.option.name
            entry.description = chosen.option.description
            entry.type = chosen.option.type
            entry.property = chosen.option.property
            if (chosen.option.value != null) {
                entry.value = chosen.option.value
            }
            entry.origin = chosen.origin
            entries << entry
        }
        if (left > 0) {
            said << "More than ${CommandOptions.MAX_OPTIONS} options are declared for commands: the last ${left} by command and name are not used.".toString()
        }
        entries
    }

    // the one declaration of a name that is used; null, after saying why, when two elements declare the name
    private static Listed chosen(String name, List<Listed> all, Set<String> said) {
        if (all.size() == 1) {
            return all[0]
        }
        // the same declaration in two elements is one, of the first of them
        if (all.every { Listed each -> each.option.sameAs(all[0].option) }) {
            return all[0]
        }
        List<Listed> own = all.findAll { Listed each -> each.origin == CommandCatalogBuilder.ORIGIN_APPLICATION }
        List<String> origins = all.collect { Listed each -> each.origin }.unique().sort()
        if (own.size() == 1) {
            List<String> others = origins.findAll { String origin -> origin != CommandCatalogBuilder.ORIGIN_APPLICATION }
            said << "The option ${name} is as the application declares it: what ${some(others)} declares for that name is not used.".toString()
            return own[0]
        }
        said << (origins.size() == 1 ?
                "The option ${name} is not used: ${origins[0]} declares it more than once, and not the same each time.".toString() :
                "The option ${name} is not used because more than one declares it, and not the same: ${some(origins)}.".toString())
        null
    }

    private static String some(List<String> all) {
        all.size() <= CommandCatalogBuilder.MAX_NAMED ? all.join(', ') :
                "${all.subList(0, CommandCatalogBuilder.MAX_NAMED).join(', ')} and ${all.size() - CommandCatalogBuilder.MAX_NAMED} more".toString()
    }

    private static class Listed {
        String origin
        CommandOptions.Declared option
    }
}
