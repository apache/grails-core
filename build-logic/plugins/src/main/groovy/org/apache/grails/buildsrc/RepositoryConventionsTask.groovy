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
package org.apache.grails.buildsrc

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException

import java.util.Set
import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
abstract class RepositoryConventionsTask extends DefaultTask {

    private static final Pattern AGENT_SKILL_PATH = Pattern.compile(/\.agents\/skills\/[A-Za-z0-9_-]+\/SKILL\.md/)
    private static final Pattern COMMIT_SHA = Pattern.compile(/^[0-9a-f]{40}$/)
    private static final Pattern DOCKER_IMAGE_DIGEST = Pattern.compile(/^docker:\/\/[^@\s]+@sha256:[0-9a-f]{64}$/)
    private static final Pattern CONTAINER_IMAGE_DIGEST = Pattern.compile(/^[^@\s]+@sha256:[0-9a-f]{64}$/)

    @Internal
    abstract DirectoryProperty getRepositoryDirectory()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getConventionSources()

    @OutputFile
    abstract RegularFileProperty getReportFile()

    @TaskAction
    void validateRepositoryConventions() {
        File root = repositoryDirectory.get().asFile
        List<File> files = conventionSources.files.toList()
        List<String> violations = []
        validateSkills(root, files, violations)
        validateActions(root, files, violations)
        validateProperties(root, files, violations)
        writeReport(violations)
        if (!violations.isEmpty()) {
            List<String> safeViolations = violations.collect { String violation -> sanitizeViolation(violation) }
            throw new GradleException("Repository convention violations:\n - ${safeViolations.join('\n - ')}\nSee ${reportFile.get().asFile}")
        }
    }

    private static void validateSkills(File root, List<File> files, List<String> violations) {
        List<File> skills = files.findAll { relativePath(root, it) ==~ /^\.agents\/skills\/[^\/]+\/SKILL\.md$/ }.sort()
        Map<String, File> names = [:]
        Set<String> canonicalPaths = []
        skills.each { File skill ->
            String path = relativePath(root, skill)
            String directoryName = skill.parentFile.name
            Map<String, String> metadata = frontMatter(skill, path, violations)
            ['name', 'description', 'license'].each { String key ->
                if (!metadata[key]) {
                    violations.add("${path}: skill front matter is missing '${key}'".toString())
                }
            }
            String name = metadata['name']
            if (name && name != directoryName) {
                violations.add("${path}: skill name '${name}' does not match directory '${directoryName}'".toString())
            }
            if (name && names.containsKey(name)) {
                violations.add("${path}: skill name '${name}' duplicates ${relativePath(root, names[name])}".toString())
            } else if (name) {
                names[name] = skill
            }
            canonicalPaths << path
        }

        File agents = new File(root, 'AGENTS.md')
        if (!agents.isFile()) {
            violations << 'AGENTS.md: file is missing'
            return
        }
        Set<String> documentedPaths = []
        Matcher matcher = AGENT_SKILL_PATH.matcher(agents.text)
        while (matcher.find()) {
            documentedPaths << matcher.group()
        }
        canonicalPaths.each { String path ->
            if (!documentedPaths.contains(path)) {
                violations.add("AGENTS.md: missing canonical skill path '${path}'".toString())
            }
        }
        documentedPaths.each { String path ->
            if (!new File(root, path).isFile()) {
                violations.add("AGENTS.md: skill path '${path}' does not exist".toString())
            }
        }
    }

    private static Map<String, String> frontMatter(File skill, String path, List<String> violations) {
        List<String> lines = skill.readLines()
        if (lines.isEmpty() || lines[0] != '---') {
            return [:]
        }
        int end = -1
        for (int index = 1; index < lines.size(); index++) {
            if (lines[index] == '---') {
                end = index
                break
            }
        }
        if (end < 0) {
            return [:]
        }
        Object document
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            document = new Yaml(new SafeConstructor(options)).load(lines.subList(1, end).join('\n'))
        } catch (YAMLException exception) {
            violations.add(sanitizeViolation("${path}: malformed skill front matter: ${exception.message}".toString()))
            return [:]
        }
        if (!(document instanceof Map)) {
            violations.add(sanitizeViolation("${path}: skill front matter must be a YAML mapping".toString()))
            return [:]
        }
        Map<String, String> values = [:]
        ['name', 'description', 'license'].each { String key ->
            Object value = ((Map<?, ?>) document).get(key)
            if (value instanceof String) {
                values[key] = (String) value
            } else if (value != null) {
                violations.add(sanitizeViolation("${path}: skill front matter field '${key}' must be a string".toString()))
            }
        }
        values
    }

    private static void validateActions(File root, List<File> files, List<String> violations) {
        Map<String, String> actionShas = [:]
        Map<String, String> actionFiles = [:]
        Set<String> validatedManifests = []
        files.findAll { File file -> isActionManifest(root, file) }.sort().each { File manifest ->
            validateActionManifest(root, manifest, actionShas, actionFiles, violations, validatedManifests)
        }
    }

    private static void validateActionManifest(File root, File manifest, Map<String, String> actionShas,
            Map<String, String> actionFiles, List<String> violations, Set<String> validatedManifests) {
        String canonicalPath = manifest.canonicalPath
        if (!validatedManifests.add(canonicalPath)) {
            return
        }
        String path = relativePath(root, manifest)
        Object document = parseYaml(manifest, path, violations)
        if (document != null) {
            validateDockerActionImage(document, path, violations)
            if (isWorkflowManifest(root, manifest)) {
                validateWorkflowContainerImages(document, path, violations)
                validateWorkflowUses(root, document, path, actionShas, actionFiles, violations, validatedManifests)
            } else {
                validateCompositeActionUses(root, document, path, actionShas, actionFiles, violations, validatedManifests)
            }
        }
    }

    private static boolean isActionManifest(File root, File file) {
        String path = relativePath(root, file)
        isWorkflowManifest(root, file) ||
                path ==~ /(?:^|.*\/)action\.ya?ml$/
    }

    private static boolean isWorkflowManifest(File root, File file) {
        relativePath(root, file) ==~ /^\.github\/workflows\/[^\/]+\.ya?ml$/
    }

    private static Object parseYaml(File manifest, String path, List<String> violations) {
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            new Yaml(new SafeConstructor(options)).load(manifest.text)
        } catch (YAMLException exception) {
            violations.add("${path}: malformed YAML: ${exception.message}".toString())
            null
        }
    }

    private static void validateDockerActionImage(Object document, String path, List<String> violations) {
        if (!(document instanceof Map)) {
            return
        }
        Object runs = ((Map<?, ?>) document).get('runs')
        Object using = runs instanceof Map ? ((Map<?, ?>) runs).get('using') : null
        if (!(using instanceof String) || !((String) using).equalsIgnoreCase('docker')) {
            return
        }
        Object image = ((Map<?, ?>) runs).get('image')
        String location = '$.runs.image'
        if (!(image instanceof String)) {
            violations.add("${path}:${location}: Docker action image must be a string".toString())
        } else if (((String) image).regionMatches(true, 0, 'docker://', 0, 'docker://'.length()) && !DOCKER_IMAGE_DIGEST.matcher((String) image).matches()) {
            violations.add("${path}:${location}: Docker action image '${image}' must use an immutable sha256 digest".toString())
        }
    }

    private static void validateWorkflowContainerImages(Object document, String path, List<String> violations) {
        if (!(document instanceof Map)) {
            return
        }
        Object jobs = ((Map<?, ?>) document).get('jobs')
        if (!(jobs instanceof Map)) {
            return
        }
        ((Map<?, ?>) jobs).each { Object jobName, Object job ->
            if (!(job instanceof Map)) {
                return
            }
            String jobLocation = "\$.jobs.${jobName}"
            Map<?, ?> jobDefinition = (Map<?, ?>) job
            if (jobDefinition.containsKey('container')) {
                Object container = jobDefinition.get('container')
                if (container instanceof Map) {
                    validateContainerImage(((Map<?, ?>) container).get('image'), "${jobLocation}.container.image", path, violations)
                } else {
                    validateContainerImage(container, "${jobLocation}.container", path, violations)
                }
            }
            Object services = jobDefinition.get('services')
            if (services instanceof Map) {
                ((Map<?, ?>) services).each { Object serviceName, Object service ->
                    if (service instanceof Map && ((Map<?, ?>) service).containsKey('image')) {
                        validateContainerImage(((Map<?, ?>) service).get('image'), "${jobLocation}.services.${serviceName}.image", path,
                                violations)
                    }
                }
            }
        }
    }

    private static void validateContainerImage(Object image, String location, String path, List<String> violations) {
        if (!(image instanceof String)) {
            violations.add("${path}:${location}: container image must be a string".toString())
        } else if (!CONTAINER_IMAGE_DIGEST.matcher((String) image).matches()) {
            violations.add("${path}:${location}: container image '${image}' must use an immutable sha256 digest".toString())
        }
    }

    private static void validateWorkflowUses(File root, Object document, String path, Map<String, String> actionShas,
            Map<String, String> actionFiles, List<String> violations, Set<String> validatedManifests) {
        if (!(document instanceof Map)) {
            return
        }
        Map<?, ?> workflow = (Map<?, ?>) document
        validateStepUses(root, workflow.get('steps'), '$.steps', path, actionShas, actionFiles, violations, validatedManifests)
        Object jobs = workflow.get('jobs')
        if (!(jobs instanceof Map)) {
            return
        }
        ((Map<?, ?>) jobs).each { Object jobName, Object job ->
            if (!(job instanceof Map)) {
                return
            }
            Map<?, ?> jobDefinition = (Map<?, ?>) job
            String jobLocation = "\$.jobs.${jobName}"
            if (jobDefinition.containsKey('uses')) {
                validateActionUse(root, jobDefinition.get('uses'), "${jobLocation}.uses", path, actionShas, actionFiles, violations,
                        validatedManifests)
            }
            validateStepUses(root, jobDefinition.get('steps'), "${jobLocation}.steps", path, actionShas, actionFiles, violations,
                    validatedManifests)
        }
    }

    private static void validateCompositeActionUses(File root, Object document, String path, Map<String, String> actionShas,
            Map<String, String> actionFiles, List<String> violations, Set<String> validatedManifests) {
        if (!(document instanceof Map)) {
            return
        }
        Object runs = ((Map<?, ?>) document).get('runs')
        if (runs instanceof Map) {
            validateStepUses(root, ((Map<?, ?>) runs).get('steps'), '$.runs.steps', path, actionShas, actionFiles, violations,
                    validatedManifests)
        }
    }

    private static void validateStepUses(File root, Object steps, String location, String path, Map<String, String> actionShas,
            Map<String, String> actionFiles, List<String> violations, Set<String> validatedManifests) {
        if (!(steps instanceof Iterable)) {
            return
        }
        int index = 0
        ((Iterable<?>) steps).each { Object step ->
            if (step instanceof Map && ((Map<?, ?>) step).containsKey('uses')) {
                validateActionUse(root, ((Map<?, ?>) step).get('uses'), "${location}[${index}].uses", path, actionShas, actionFiles,
                        violations, validatedManifests)
            }
            index++
        }
    }

    private static void validateActionUse(File root, Object value, String location, String path, Map<String, String> actionShas,
            Map<String, String> actionFiles, List<String> violations, Set<String> validatedManifests) {
        if (!(value instanceof String)) {
            violations.add("${path}:${location}: 'uses' must be a string".toString())
            return
        }
        String use = (String) value
        if (use.startsWith('./')) {
            validateLocalAction(root, use, location, path, actionShas, actionFiles, violations, validatedManifests)
            return
        }
        if (use.startsWith('docker://')) {
            if (!DOCKER_IMAGE_DIGEST.matcher(use).matches()) {
                violations.add("${path}:${location}: Docker action '${use}' must use an immutable sha256 digest".toString())
            }
            return
        }
        int separator = use.lastIndexOf('@')
        if (separator <= 0 || separator == use.length() - 1) {
            violations.add("${path}:${location}: action '${use}' must use a lowercase 40-hex commit SHA".toString())
            return
        }
        String action = use.substring(0, separator)
        String sha = use.substring(separator + 1)
        if (!COMMIT_SHA.matcher(sha).matches()) {
            violations.add("${path}:${location}: action '${action}' uses '${sha}', not a lowercase 40-hex commit SHA".toString())
        } else if (actionShas.containsKey(action) && actionShas[action] != sha) {
            violations.add("${path}:${location}: action '${action}' uses ${sha}, inconsistent with ${actionShas[action]} in ${actionFiles[action]}".toString())
        } else {
            actionShas[action] = sha
            actionFiles[action] = path
        }
    }

    private static void validateLocalAction(File root, String use, String location, String path,
            Map<String, String> actionShas, Map<String, String> actionFiles, List<String> violations,
            Set<String> validatedManifests) {
        File canonicalRoot = root.canonicalFile
        File target = new File(root, use.substring(2)).canonicalFile
        if (!target.toPath().startsWith(canonicalRoot.toPath())) {
            violations.add("${path}:${location}: local action '${use}' resolves outside the repository".toString())
            return
        }
        if (target.isFile() && target.name ==~ /.*\.ya?ml/) {
            validateActionManifest(root, target, actionShas, actionFiles, violations, validatedManifests)
            return
        }
        ['action.yml', 'action.yaml'].each { String manifestName ->
            File manifest = new File(target, manifestName)
            if (manifest.isFile()) {
                validateActionManifest(root, manifest, actionShas, actionFiles, violations, validatedManifests)
            }
        }
    }

    private static void validateProperties(File root, List<File> files, List<String> violations) {
        files.findAll { File file -> file.name.startsWith('messages') && file.name.endsWith('.properties') }.sort().each { File file ->
            Map<String, Integer> keys = [:]
            logicalPropertiesLines(file).each { PropertiesLine line ->
                String key = propertyKey(line.content)
                if (!key) {
                    return
                }
                if (keys.containsKey(key)) {
                    violations.add("${relativePath(root, file)}:${line.number}: duplicate message key '${key}' (first declared at line ${keys[key]})".toString())
                } else {
                    keys[key] = line.number
                }
            }
        }
    }

    private static List<PropertiesLine> logicalPropertiesLines(File file) {
        List<PropertiesLine> result = []
        String content = null
        int start = 0
        file.readLines().eachWithIndex { String line, int index ->
            if (content == null) {
                String trimmed = line.trim()
                if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) {
                    return
                }
                content = line
                start = index + 1
            } else {
                content += line.replaceFirst(/^\s+/, '')
            }
            if (continues(content)) {
                content = content.substring(0, content.length() - 1)
            } else {
                result << new PropertiesLine(start, content)
                content = null
            }
        }
        if (content != null) {
            result << new PropertiesLine(start, content)
        }
        result
    }

    private static boolean continues(String line) {
        int count = 0
        for (int index = line.length() - 1; index >= 0 && line.charAt(index) == '\\'; index--) {
            count++
        }
        count % 2 == 1
    }

    private static String propertyKey(String line) {
        Properties properties = new Properties()
        try {
            properties.load(new StringReader(line))
        } catch (IllegalArgumentException ignored) {
            return null
        }
        properties.stringPropertyNames().find()
    }

    private void writeReport(List<String> violations) {
        File output = reportFile.get().asFile
        output.parentFile.mkdirs()
        StringBuilder text = new StringBuilder('# Repository Conventions\n\n')
        if (violations.isEmpty()) {
            text.append('No violations found!\n')
        } else {
            text.append('| Violation |\n| :--- |\n')
            violations.each { String violation -> text.append("| ${sanitizeViolation(violation)} |\n") }
        }
        output.text = text.toString()
    }

    private static String sanitizeViolation(String violation) {
        violation.replace('\r', '\\r').replace('\n', '\\n').replace('|', '\\|')
    }

    private static String relativePath(File root, File file) {
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)
    }

    private static final class PropertiesLine {
        final int number
        final String content

        PropertiesLine(int number, String content) {
            this.number = number
            this.content = content
        }
    }
}
