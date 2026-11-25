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
package org.grails.forge.cli.command

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.UsageHelp
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.Feature
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine

/**
 * {@code feature search}: the features that have a text in their name, their title, their
 * description or their category, among those there are for the application of the current directory
 * or for a new application of a type.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureSearchCommand.NAME, description = [
        'Lists the features that have a text in their name, their title, their description or their category, in whichever case.',
        'In the directory of an application they are the features of Grails for its type, and those of the feature packs it was generated with, ' +
                'given as to feature list; each says whether the application has it. Outside a project, and with --type, they are the features a new ' +
                'application of the type is created with, and each says whether such an application has it by default.',
        '@|bold feature info|@ says more of one feature.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureSearchCommand extends FeatureQueryCommand {

    public static final String NAME = 'search'

    @CommandLine.Parameters(arity = '1', paramLabel = 'TEXT',
            description = 'The text the features are listed by: one line, which a feature has in its name, its title, its description or its category.')
    String text

    @CommandLine.Option(names = ['--json'], description = 'Write the features that were found as one JSON document for a tool to read, and nothing else.')
    boolean json

    @Autowired
    FeatureSearchCommand(ProjectGenerator projectGenerator) {
        super({ projectGenerator })
    }

    /** On a Forge application that is started when the command first needs it. */
    FeatureSearchCommand(ForgeContext context) {
        super(generatorOf(context))
    }

    @Override
    Integer call() throws Exception {
        if (text.trim().isEmpty() || text.length() > FeatureScope.WIDTH || text != UsageHelp.line(text)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "The text to search the features by is one line of at most ${FeatureScope.WIDTH} characters")
        }
        List<String> notes = []
        FeatureScope scope = scope(notes)
        if (scope == null) {
            return 1
        }
        String sought = text.toLowerCase(Locale.ROOT)
        List<Feature> found = scope.visible.findAll { Feature feature ->
            [feature.getName(), feature.getTitle(), feature.getDescription(), feature.getCategory()].any { String each ->
                each != null && each.toLowerCase(Locale.ROOT).contains(sought)
            }
        }
        Set<String> defaults = scope.defaults
        List<String> has = scope.has
        if (json) {
            Map<String, Object> document = new LinkedHashMap<>()
            document.put('format', FORMAT)
            document.put('projectDirectory', scope.directory?.toString())
            document.put('applicationType', scope.typeName)
            document.put('text', text)
            document.put('notes', notes)
            document.put('features', found.collect { Feature feature ->
                Map<String, Object> entry = new LinkedHashMap<>()
                entry.put('name', feature.getName())
                entry.put('title', feature.getTitle())
                entry.put('description', feature.getDescription())
                entry.put('category', feature.getCategory())
                entry.put('preview', feature.isPreview())
                entry.put('community', feature.isCommunity())
                entry.put('default', defaults == null ? null : defaults.contains(feature.getName()))
                entry.put('inApplication', has == null ? null : has.contains(feature.getName()))
                return entry
            })
            written(JsonOutput.toJson(document))
            return 0
        }
        for (String line : notes) {
            note(line)
        }
        if (found.isEmpty()) {
            out("@|blue ||@ No feature of ${scope.described} has ${shown(text)} in its name, its title, its description or its category")
            return 0
        }
        out("@|blue ||@ ${found.size()} ${found.size() == 1 ? 'feature' : 'features'} of ${scope.described} ${found.size() == 1 ? 'has' : 'have'} ${shown(text)} " +
                'in the name, the title, the description or the category:')
        Map<String, List<Feature>> byCategory = new TreeMap<>(found.groupBy { Feature feature -> feature.getCategory() ?: '' })
        byCategory.each { String category, List<Feature> features ->
            out("  @|bold ${shown(category)}|@")
            for (Feature feature : features) {
                String name = feature.getName() + (feature.isPreview() ? ' [PREVIEW]' : '') + (feature.isCommunity() ? ' [COMMUNITY]' : '')
                String status = has != null ? (has.contains(feature.getName()) ? ' (the application has it)' : '') :
                        defaults != null && defaults.contains(feature.getName()) ? ' (by default)' : ''
                for (String line : FeatureScope.row(shown(name), shown(feature.getDescription() ?: '') + status)) {
                    out(line)
                }
            }
        }
        out("@|blue ||@ More of one feature: ${UsageHelp.toType(spec, "${FeatureCommand.NAME} ${FeatureInfoCommand.NAME} FEATURE")}")
        return 0
    }
}
