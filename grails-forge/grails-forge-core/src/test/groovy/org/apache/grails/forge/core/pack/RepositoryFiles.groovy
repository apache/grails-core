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
 * chapter of the guide, the class with the table of the template API, and the views of the example
 * applications. The build declares each as an input of the tests. Where Forge is built from sources
 * that do not have them, they are not there, and the specs that read them are skipped.
 */
final class RepositoryFiles {

    static final String GUIDE = 'grails-doc/src/en/guide/plugins/forgeFeaturePacks.adoc'
    static final String PACK_MODEL = 'grails-forge/grails-forge-core/src/main/groovy/org/apache/grails/forge/core/pack/PackModel.groovy'
    static final List<String> VIEWS = ['grails-test-examples', 'grails-spring-security']

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
     * @return the views of the example applications, by their path in the sources, in order of path
     */
    static Map<String, File> views() {
        File root = root()
        Map<String, File> views = new TreeMap<>()
        if (root != null) {
            for (String below : VIEWS) {
                File directory = new File(root, below)
                if (directory.directory) {
                    directory.eachFileRecurse { File file ->
                        String path = root.toPath().relativize(file.toPath()).toString().replace(File.separator, '/')
                        if (file.file && path.endsWith('.gsp') && path.contains('/grails-app/views/') && !path.contains('/build/')) {
                            views.put(path, file)
                        }
                    }
                }
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
