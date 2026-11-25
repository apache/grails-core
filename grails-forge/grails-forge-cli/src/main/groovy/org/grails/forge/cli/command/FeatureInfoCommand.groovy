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
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.FeatureRequestException
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine

/**
 * {@code feature info}, and by its other name {@code feature show}: what is known of one feature.
 * What the feature is, is read from the feature: its title, its description, its category, the types
 * of application it is for, what it requires and where its documentation is. What it comes to in an
 * application is worked out by the generator: in the directory of an application that does not have
 * it, from what {@code feature add} would do there, and otherwise from an application of the type
 * that is generated with the feature and without it.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureInfoCommand.NAME, aliases = [FeatureInfoCommand.OTHER_NAME], description = [
        'Says what is known of one feature: its title, what it is for, its category, the types of application it is for, the features it requires, ' +
                'those it is exclusive with and those whose place it takes, whether an application has it by default, and where its documentation is.',
        'In the directory of an application it says whether the application has the feature, and which files and which lines of the build files ' +
                'feature add would add, change and remove there. Outside a project, with --type, and for a feature the application has, these are ' +
                'worked out for a new application of the type.',
        'For the feature of a feature pack that is not part of Grails, the pack and the coordinates of its plugin are named. ' +
                'The command is also there as @|bold feature show|@.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureInfoCommand extends FeatureQueryCommand {

    public static final String NAME = 'info'

    /** The other name of the command */
    public static final String OTHER_NAME = 'show'

    private static final int MAX_NAMED = 5
    private static final String NONE = 'none'

    @CommandLine.Parameters(arity = '1', paramLabel = 'FEATURE', completionCandidates = KnownFeatureCandidates,
            description = 'The name of the feature, as feature list and feature search have it.')
    String feature

    @CommandLine.Option(names = ['--json'], description = 'Write what is known of the feature as one JSON document for a tool to read, and nothing else.')
    boolean json

    @Autowired
    FeatureInfoCommand(ProjectGenerator projectGenerator) {
        super({ projectGenerator })
    }

    /** On a Forge application that is started when the command first needs it. */
    FeatureInfoCommand(ForgeContext context) {
        super(generatorOf(context))
    }

    @Override
    Integer call() throws Exception {
        List<String> notes = []
        FeatureScope scope = scope(notes)
        if (scope == null) {
            return 1
        }
        Feature found = scope.available.findFeature(feature).filter { Feature each -> each.isVisible() }.orElse(null)
        if (found == null) {
            return failed(scope, notes, unknown(scope))
        }
        String name = found.getName()
        Set<String> defaults = scope.defaults
        List<String> has = scope.has
        boolean inApplication = has != null && has.contains(name)

        Map<String, Object> info = new LinkedHashMap<>()
        info.put('name', name)
        info.put('title', found.getTitle())
        info.put('description', found.getDescription())
        info.put('category', found.getCategory())
        info.put('preview', found.isPreview())
        info.put('community', found.isCommunity())
        info.put('applicationTypes', ApplicationType.values().findAll { ApplicationType each -> found.supports(each) }.collect { ApplicationType each -> FeatureScope.written(each) })
        info.put('requires', new ArrayList<String>(found.getDependentFeatures()))
        info.put('exclusiveGroup', found instanceof OneOfFeature ? ((OneOfFeature) found).getGroup() : null)
        info.put('conflictsWith', exclusiveWith(scope, found))
        info.put('default', defaults == null ? null : defaults.contains(name))
        info.put('inApplication', has == null ? null : inApplication)
        info.put('documentation', found.getDocumentation())
        info.put('thirdPartyDocumentation', found.getThirdPartyDocumentation())
        info.put('featurePack', packOf(found))
        Worked worked = scope.isOfProject() && !inApplication ? inProject(scope, name) : null
        if (worked == null || worked.contribution == null) {
            Worked generated = generated(scope, name, defaults != null && defaults.contains(name))
            generated.addable = worked == null ? null : worked.addable
            generated.reason = worked == null ? generated.reason : worked.reason
            worked = generated
        }
        info.put('addable', scope.isOfProject() && !inApplication ? worked.addable : null)
        info.put('notAddedBecause', worked.reason)
        info.put('replaceOption', worked.replaceOption)
        info.put('replaces', worked.replaces)
        info.put('bringsWithIt', worked.with)
        info.put('contributionTo', worked.contribution == null ? null : worked.ofProject ? 'application' : 'new-application')
        info.put('files', worked.contribution?.files)
        info.put('build', worked.contribution?.build)
        if (json) {
            written(JsonOutput.toJson(document(scope, notes, info, null)))
            return 0
        }
        for (String line : notes) {
            note(line)
        }
        print(scope, found, info, worked)
        return 0
    }

    // what feature add would do with the feature in the application of the current directory
    private Worked inProject(FeatureScope scope, String name) {
        Worked worked = new Worked()
        worked.ofProject = true
        try {
            FeatureAddition addition = new FeatureAddition(getProjectGenerator())
            ChangePlan plan
            try {
                plan = addition.plan(scope.directory, [name], getOperatingSystem(), ConsoleOutput.NOOP, false, scope.givenPacks)
            } catch (FeatureRequestException e) {
                if (e.getReason() != FeatureRequestException.REPLACE_REQUIRED) {
                    throw e
                }
                // what it comes to with the option that feature add then names
                worked.replaceOption = true
                plan = addition.plan(scope.directory, [name], getOperatingSystem(), ConsoleOutput.NOOP, true, scope.givenPacks)
            }
            worked.contribution = FeatureContribution.ofPlan(plan)
            worked.replaces = plan.isRefused() ? new ArrayList<String>(plan.lostFeatures) : new ArrayList<String>(plan.replaced)
            worked.with = plan.added.findAll { String each -> each != name && scope.available.findFeature(each).isPresent() }.sort()
            int conflicts = plan.getConflicts().size()
            worked.addable = !plan.isRefused() && conflicts == 0
            worked.reason = plan.isRefused() ? shown(plan.refusal) : conflicts == 0 ? null :
                    "${conflicts} ${conflicts == 1 ? 'file' : 'files'} of the application cannot be written without losing what the application has: " +
                            "${UsageHelp.toType(spec, "${FeatureCommand.NAME} ${FeatureAddCommand.NAME} ${name} --dry-run")} names ${conflicts == 1 ? 'it' : 'them'}"
        } catch (FeatureSelectionException | ProjectStateException | ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            worked.addable = false
            worked.reason = shown(ProjectMigrateCommand.withMove(spec, scope.directory, e))
            worked.contribution = null
        }
        return worked
    }

    // an application of the type generated with the feature, and without it
    private Worked generated(FeatureScope scope, String name, boolean byDefault) {
        Worked worked = new Worked()
        ProjectGenerator generator = getProjectGenerator()
        try {
            // an application has a default without being asked for it, and is without it when it is left out
            GeneratorContext without = generator.createGeneratorContext(scope.type, scope.project, scope.options, getOperatingSystem(), Collections.<String>emptyList(),
                    ConsoleOutput.NOOP, scope.packs, byDefault ? [name] : Collections.<String>emptyList())
            GeneratorContext with = generator.createGeneratorContext(scope.type, scope.project, scope.options, getOperatingSystem(), [name], ConsoleOutput.NOOP, scope.packs)
            List<String> before = without.getFeatures().getFeatures()*.getName()
            List<String> after = with.getFeatures().getFeatures()*.getName()
            worked.replaces = before.findAll { String each -> !after.contains(each) }.sort()
            worked.with = after.findAll { String each -> each != name && !before.contains(each) && scope.available.findFeature(each).isPresent() }.sort()
            worked.contribution = FeatureContribution.ofGenerated(files(scope, without), files(scope, with))
        } catch (Exception e) {
            // a feature that is not selected alone with these options, or a default an application is not generated without
            worked.reason = "What the feature contributes to ${scope.isOfProject() ? 'a new application like this one' : scope.described} was not worked out: " +
                    shown(e.getMessage() ?: e.getClass().getSimpleName())
        }
        return worked
    }

    private Map<String, String> files(FeatureScope scope, GeneratorContext context) {
        MapOutputHandler handler = new MapOutputHandler()
        getProjectGenerator().generate(scope.type, scope.project, handler, context)
        return handler.getProject()
    }

    // the other features of the exclusive group of the feature, of which an application has one
    private static List<String> exclusiveWith(FeatureScope scope, Feature feature) {
        if (!(feature instanceof OneOfFeature)) {
            return Collections.<String>emptyList()
        }
        String group = ((OneOfFeature) feature).getGroup()
        return scope.visible.findAll { Feature each -> each instanceof OneOfFeature && ((OneOfFeature) each).getGroup() == group && each.getName() != feature.getName() }*.getName()
    }

    // the pack of a feature that is not part of Grails, and the plugin whose jar has it
    private static Map<String, Object> packOf(Feature feature) {
        if (!(feature instanceof PackFeature) || ((PackFeature) feature).getPack().isBundled()) {
            return null
        }
        Pack pack = ((PackFeature) feature).getPack()
        Map<String, Object> said = new LinkedHashMap<>()
        said.put('name', pack.descriptor.name)
        said.put('coordinates', pack.artifact?.coordinates)
        said.put('origin', pack.origin)
        said.put('contentSha256', pack.contentSha256)
        return said
    }

    private void print(FeatureScope scope, Feature found, Map<String, Object> info, Worked worked) {
        String name = found.getName()
        String title = found.getTitle()
        out("Feature: ${shown(name)}${title && title != name ? " (${shown(title)})" : ''}${found.isPreview() ? ' [PREVIEW]' : ''}${found.isCommunity() ? ' [COMMUNITY]' : ''}")
        if (found.getDescription() && found.getDescription() != name) {
            out('')
            out(shown(found.getDescription()))
        }
        out('')
        out("* Category: ${shown(found.getCategory() ?: NONE)}")
        out("* Application types: ${((List<String>) info.get('applicationTypes')).join(', ')}")
        if (info.get('default') != null) {
            out("* By default: ${info.get('default') ? 'yes' : 'no'}, in ${scope.isOfProject() ? "a new application of type ${scope.typeName} with the options of this one" : scope.described}")
        }
        if (scope.isOfProject()) {
            out("* In this application: ${info.get('inApplication') ? 'yes' : 'no'}")
        }
        out("* Requires: ${shownNames((List<String>) info.get('requires'))}")
        if (info.get('exclusiveGroup') != null) {
            out("* Exclusive group ${shown((String) info.get('exclusiveGroup'))}: an application has one of it, so not this feature together with " +
                    shownNames((List<String>) info.get('conflictsWith')))
        }
        if (worked.replaces) {
            out("* Takes the place of: ${shownNames(worked.replaces)}" + (worked.replaceOption ? ', with --replace only: it was asked for by name' : ''))
        }
        if (worked.with) {
            out("* Brings with it: ${shownNames(worked.with)}")
        }
        if (found.getDocumentation()) {
            out("* Documentation: ${shown(found.getDocumentation())}")
        }
        if (found.getThirdPartyDocumentation()) {
            out("* Third-party documentation: ${shown(found.getThirdPartyDocumentation())}")
        }
        Map<String, Object> pack = (Map<String, Object>) info.get('featurePack')
        if (pack != null) {
            out("* Feature pack: ${shown((String) pack.get('name'))}, " + (pack.get('coordinates') ? "of the plugin ${shown((String) pack.get('coordinates'))}" :
                    "of ${shown((String) pack.get('origin'))}"))
        }
        if (info.get('addable') == Boolean.FALSE) {
            out("* feature add does not add it to this application: ${worked.reason}")
        } else if (worked.reason != null) {
            out("* ${worked.reason}")
        }
        if (worked.contribution == null) {
            return
        }
        String where = worked.ofProject ? 'In this application feature add would change' : "In ${scope.isOfProject() ? "a new application of type ${scope.typeName}" : scope.described} it changes"
        if (worked.contribution.files.isEmpty()) {
            out("* ${where} no file")
            return
        }
        out("* ${where} ${worked.contribution.files.size()} ${worked.contribution.files.size() == 1 ? 'file' : 'files'}:")
        for (Map<String, String> file : worked.contribution.files) {
            out("    ${file.get('change').padRight(8)} ${shown(file.get('path'))}")
            Map<String, Object> build = worked.contribution.build.find { Map<String, Object> each -> each.get('file') == file.get('path') }
            for (String line : (build == null ? Collections.<String>emptyList() : (List<String>) build.get('added'))) {
                written("             + ${line.trim()}")
            }
            for (String line : (build == null ? Collections.<String>emptyList() : (List<String>) build.get('removed'))) {
                written("             - ${line.trim()}")
            }
        }
    }

    private static String shownNames(List<String> names) {
        return names ? names.collect { String each -> shown(each) }.join(', ') : NONE
    }

    // a name that is no feature of the application: the types it is a feature of, or the names that are like it
    private String unknown(FeatureScope scope) {
        String given = FeatureSelectionException.shown(feature)
        List<String> types = []
        for (ApplicationType each : ApplicationType.values()) {
            if (each != scope.type && getProjectGenerator().availableFeatures(each, scope.isOfProject() ? Collections.<Pack>emptyList() : scope.packs)
                    .findFeature(feature).filter { Feature other -> other.isVisible() }.isPresent()) {
                types << FeatureScope.written(each)
            }
        }
        if (!types.isEmpty()) {
            return "${given} is no feature of ${scope.described}: it is a feature of applications of type ${types.join(', ')}. " +
                    "${UsageHelp.toType(spec, "${FeatureCommand.NAME} ${NAME} ${given} --type ${types.first()}")} says what is known of it"
        }
        String sought = feature.toLowerCase(Locale.ROOT)
        List<String> close = sought.length() < 3 ? Collections.<String>emptyList() : scope.visible*.getName().findAll { String each ->
            each.toLowerCase(Locale.ROOT).contains(sought) || sought.contains(each.toLowerCase(Locale.ROOT))
        }
        return "There is no feature ${given} for ${scope.described}." +
                (close ? " Did you mean: ${close.take(MAX_NAMED).join(', ')}${close.size() > MAX_NAMED ? " (and ${close.size() - MAX_NAMED} more)" : ''}?" : '') +
                " ${UsageHelp.toType(spec, "${FeatureCommand.NAME} ${FeatureSearchCommand.NAME} TEXT")} lists the features that have a text in their name or their description"
    }

    private static Map<String, Object> document(FeatureScope scope, List<String> notes, Map<String, Object> info, String error) {
        Map<String, Object> document = new LinkedHashMap<>()
        document.put('format', FORMAT)
        document.put('projectDirectory', scope.directory?.toString())
        document.put('applicationType', scope.typeName)
        document.put('notes', notes)
        document.put('feature', info)
        if (error != null) {
            document.put('error', error)
        }
        return document
    }

    private int failed(FeatureScope scope, List<String> notes, String message) {
        if (json) {
            // a tool is told in the document what a person is told in the error
            written(JsonOutput.toJson(document(scope, notes, null, message)))
        } else {
            for (String line : notes) {
                note(line)
            }
        }
        err(message)
        return 1
    }

    /** What a feature comes to in an application, as the generator works it out. */
    @CompileStatic
    private static final class Worked {

        /** Whether it was worked out for the application of the current directory, and not for a new one. */
        boolean ofProject
        Boolean addable
        String reason
        boolean replaceOption
        List<String> replaces = []
        List<String> with = []
        FeatureContribution contribution
    }
}
