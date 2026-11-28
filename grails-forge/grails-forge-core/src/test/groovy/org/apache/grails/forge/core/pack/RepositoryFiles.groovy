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
package org.apache.grails.forge.core.pack

/**
 * The files of the sources of Grails, beside Forge, that the specs of the templates of packs read: the
 * chapter of the guide and the class with the table of the template API, which the build declares as
 * inputs of the tests, and, when it is asked for, every view there is.
 */
final class RepositoryFiles {

    static final String GUIDE = 'grails-doc/src/en/guide/plugins/forgeFeaturePacks.adoc'
    static final String PACK_MODEL = 'grails-forge/grails-forge-core/src/main/groovy/org/apache/grails/forge/core/pack/PackModel.groovy'

    private RepositoryFiles() {
    }

    /**
     * @return the directory of the sources of Grails, or {@code null} when Forge is not built in them
     */
    static File root() {
        File directory = new File('').absoluteFile
        while (directory != null && !new File(directory, PACK_MODEL).file) {
            directory = directory.parentFile
        }
        return directory
    }

    static File guide() {
        File root = root()
        File guide = root == null ? null : new File(root, GUIDE)
        return guide?.file ? guide : null
    }

    static File packModel() {
        File root = root()
        return root == null ? null : new File(root, PACK_MODEL)
    }

    /**
     * @return whether the guide is there to be read. It is in a checkout of Grails, where its absence is
     * an error and not a reason to skip what reads it: only sources without a repository can be without it
     */
    static boolean hasGuide() {
        if (guide() != null) {
            return true
        }
        File root = root()
        if (root != null && new File(root, '.git').exists()) {
            throw new IllegalStateException("The guide of feature packs is not at ${GUIDE} of this checkout of Grails: the specs that hold the " +
                    'generator to it have to be told where it went')
        }
        return false
    }

    /**
     * @return every GSP file of the sources of Grails, by its path in them, in order of path
     */
    static Map<String, File> views() {
        File root = root()
        Map<String, File> views = new TreeMap<>()
        if (root != null) {
            root.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/.*\.gsp/,
                    preDir: { File directory -> directory.name in ['build', '.gradle', '.git', 'node_modules'] ? groovy.io.FileVisitResult.SKIP_SUBTREE : groovy.io.FileVisitResult.CONTINUE }) { File file ->
                views.put(root.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'), file)
            }
        }
        return views
    }

    /**
     * @param name the name of an example of the guide, which a comment {@code // example::name[]} is in front of
     * @return the text of the example, the listing that follows the comment
     */
    static String example(String name) {
        List<String> lines = guide().getText('UTF-8').readLines()
        int marker = lines.indexOf("// example::${name}[]".toString())
        assert marker >= 0: "the guide has no example ${name}"
        int start = marker + 1
        while (lines[start] != '----') {
            start++
        }
        int end = start + 1
        while (lines[end] != '----') {
            end++
        }
        return lines.subList(start + 1, end).join('\n') + '\n'
    }
}
