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
package org.apache.grails.scaffolding

import groovy.transform.CompileStatic

import grails.codegen.model.ModelBuilder

/**
 * Expands scaffolding templates into the pages the build compiles, run by the Gradle plugin's
 * {@code generateScaffoldedViews} task in a JVM on the application's own classpath.
 *
 * <p>Running here rather than in the build means a page is modelled by the application's
 * {@link ModelBuilder}, expanded by the application's Groovy and named by {@link ScaffoldedPages},
 * exactly as the resolver models, expands and names it when the view is asked for.</p>
 *
 * <pre>
 * ScaffoldedPagesGenerator &lt;plan&gt; &lt;origins&gt; &lt;output directory&gt; &lt;page encoding&gt; &lt;report&gt;
 * </pre>
 *
 * <p>Each line of the plan names a domain class and, tab separated, the templates directories to
 * expand for it. A templates directory holds a file per template path, such as {@code show.gsp} or
 * {@code admin/show.gsp}. Each line of the origins names a templates directory and, after a tab,
 * where its template came from. The pages are written in the encoding they will be compiled
 * with. What the build has to decide about is written to the report, a line apiece, tab
 * separated: each page written - {@code page}, its templates directory and its path under the
 * output directory - and each template that could not be expanded for a domain class -
 * {@code failed}, its templates directory, the domain class and why. What becomes of either depends
 * on whose template it is, which the build knows and this does not.</p>
 *
 * @since 8.0
 */
@CompileStatic
class ScaffoldedPagesGenerator implements ModelBuilder {

    /**
     * The version of the exchange with the build this speaks: the command line, and the plan,
     * origins and report files. This ships in grails-scaffolding and the build's side in the Gradle
     * plugin, so a build can pair one version with another; the plugin reads this constant from the
     * class file before running it, and expands nothing when it does not speak the same version, so a
     * mismatch leaves the pages to be expanded when rendered instead of failing the build. Raised with
     * every change to either side of the exchange, together with the plugin's.
     */
    public static final int PROTOCOL = 1

    static void main(String[] args) {
        if (args.length != 5) {
            System.err.println('Usage: ScaffoldedPagesGenerator <plan> <origins> <output directory> <page encoding> <report>')
            System.exit(2)
        }
        Map<String, List<File>> plan = [:]
        new File(args[0]).readLines('UTF-8').each { String line ->
            List<String> fields = line.split('\t').toList()*.trim().findAll { String field -> field }
            if (fields) {
                plan.put(fields.head(), fields.tail().collect { String dir -> new File(dir) })
            }
        }
        Map<File, String> origins = [:]
        new File(args[1]).readLines('UTF-8').each { String line ->
            int tab = line.indexOf('\t')
            if (tab > 0) {
                origins.put(new File(line.substring(0, tab)), line.substring(tab + 1))
            }
        }
        Result result = new ScaffoldedPagesGenerator().generate(plan, new File(args[2]), args[3], origins)
        List<String> report = result.pages.collect { Page page -> ['page', page.templatesDir.path, page.path].join('\t') }
        report.addAll(result.failures.collect { Failure failure ->
            ['failed', failure.templatesDir.path, failure.domain, failure.reason.replaceAll(/\s+/, ' ')].join('\t')
        })
        new File(args[4]).setText(report.join('\n'), 'UTF-8')
    }

    /**
     * Writes, under {@code outputDir} where the resolver looks for it, the page for each domain
     * class and each template in the directories planned for it. A template that cannot be
     * expanded for a domain class is left out, and returned with why.
     *
     * <p>A page ends with a comment naming the template it was expanded from, which renders as
     * nothing, so that a page the build reports can be traced to the template to fix.</p>
     *
     * @param plan each domain class, with the templates directories to expand for it
     * @param origins where the template in each templates directory came from; the template's own
     *     file for a directory not named
     * @return each page written, and each template that could not be expanded
     */
    Result generate(Map<String, List<File>> plan, File outputDir, String encoding = 'UTF-8', Map<File, String> origins = [:]) {
        Result result = new Result()
        plan.each { String domain, List<File> templateDirs ->
            Map<String, Object> model = model(domain).asMap()
            for (Template template : read(templateDirs, origins)) {
                String page
                try {
                    page = ScaffoldedPages.expand(template.content, model)
                }
                catch (Exception e) {
                    result.failures.add(new Failure(template.directory, template.path, template.origin, domain,
                            String.valueOf(e.cause ?: e)))
                    continue
                }
                String path = ScaffoldedPages.uri(template.path, model, template.content).substring(1)
                File target = new File(outputDir, path)
                target.parentFile.mkdirs()
                target.setText("${page}%{-- expanded from ${template.origin} for ${domain} --}%", encoding)
                result.pages.add(new Page(template.directory, path))
            }
        }
        result
    }

    /** What a generation did. */
    static final class Result {

        /** Each page written. */
        final List<Page> pages = []

        /** Each template that could not be expanded for a domain class. */
        final List<Failure> failures = []

        /** How many pages were written. */
        int getWritten() {
            pages.size()
        }

    }

    /** A page written: the templates directory it was expanded from, and its path under the output directory. */
    static final class Page {

        final File templatesDir

        final String path

        Page(File templatesDir, String path) {
            this.templatesDir = templatesDir
            this.path = path
        }

    }

    /** A template that could not be expanded for a domain class, and why. */
    static final class Failure {

        final File templatesDir

        final String templatePath

        final String origin

        final String domain

        final String reason

        Failure(File templatesDir, String templatePath, String origin, String domain, String reason) {
            this.templatesDir = templatesDir
            this.templatePath = templatePath
            this.origin = origin
            this.domain = domain
            this.reason = reason
        }

    }

    private static List<Template> read(List<File> templateDirs, Map<File, String> origins) {
        List<Template> templates = []
        for (File templatesDir : templateDirs) {
            if (!templatesDir.isDirectory()) {
                continue
            }
            templatesDir.eachFileRecurse { File file ->
                if (file.isFile() && file.name.endsWith('.gsp')) {
                    String path = templatesDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)
                    templates.add(new Template(templatesDir, path.substring(0, path.length() - '.gsp'.length()), file.bytes,
                            origins.get(templatesDir) ?: file.path))
                }
            }
        }
        templates
    }

    /** A template, by its directory and path, and where it came from. */
    private static final class Template {

        final File directory

        final String path

        final byte[] content

        final String origin

        Template(File directory, String path, byte[] content, String origin) {
            this.directory = directory
            this.path = path
            this.content = content
            this.origin = origin
        }

    }
}
