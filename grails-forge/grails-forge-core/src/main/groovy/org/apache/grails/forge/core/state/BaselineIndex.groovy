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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.grails.forge.io.OutputUtils
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * The index of the baseline of a project, {@code .grails/baseline/index.yml}: every file the generator
 * wrote, in the order of the paths, with the digest of its content, whether it is text or binary and
 * whether it can be executed. The content itself is kept under {@code .grails/baseline/files}, each
 * file under {@link #copyPath(String) the path of its copy}.
 *
 * @since 9.0
 */
@CompileStatic
final class BaselineIndex {

    /** The format of the index this version of Grails writes and reads */
    public static final int FORMAT = 1

    // a file of one of these names is read by Git wherever it is, also in the baseline, where the
    // patterns of the application would decide which of the copies are committed and how
    private static final Pattern READ_BY_GIT = Pattern.compile(/(?i)\.git(ignore|attributes)(\.baseline)*/)

    private static final String COPY_SUFFIX = '.baseline'

    final List<BaselineFile> files

    BaselineIndex(List<BaselineFile> files) {
        this.files = Collections.unmodifiableList(new ArrayList<BaselineFile>(files))
    }

    /**
     * @param path the path of a file of the application
     * @return the file as the index has it, or {@code null} when the generator did not write it
     */
    BaselineFile find(String path) {
        return files.find { BaselineFile each -> each.path == path }
    }

    @PackageScope
    byte[] toBytes() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('format', FORMAT)
        map.put('files', files.collect { BaselineFile each -> each.toMap() })
        return StateYaml.dump(map)
    }

    @PackageScope
    static BaselineIndex read(byte[] bytes) {
        StateSection root = new StateSection(ProjectState.BASELINE_INDEX, '', StateYaml.load(bytes, ProjectState.BASELINE_INDEX, StateLimits.INDEX_LINES))
        ProjectManifest.format(root, FORMAT)
        List<BaselineFile> files = root.sections('files', StateLimits.MAX_FILES).collect { StateSection each -> BaselineFile.read(each) }
        root.end()
        // one file for a file system that ignores the case or the composed form of letters is one file
        Set<String> seen = new HashSet<>()
        for (BaselineFile file : files) {
            if (!seen.add(folded(file.path))) {
                throw new ProjectStateException("${ProjectState.BASELINE_INDEX}: the file ${StateYaml.shown(file.path, StateYaml.MAX_ECHO)} is listed more than once")
            }
        }
        return new BaselineIndex(files)
    }

    /**
     * @param path the path of a file of an application, with {@code /} between its steps
     * @return whether the baseline can hold the file: the path stays inside the project, every step
     * of it is a name every file system takes, and it is not in the state of the project itself
     */
    static boolean isBaselinePath(String path) {
        return OutputUtils.isRelativePath(path) && PackDescriptorParser.isPathBelowRoot(path) && !ProjectState.isStatePath(path)
    }

    /**
     * Where the copy of a file is under {@code .grails/baseline/files}: under its own path, but for a
     * step that is {@code .gitignore} or {@code .gitattributes}, which is written with
     * {@code .baseline} after it, as is a step that is one of the two with {@code .baseline} after it
     * already, so that no two files have one copy. The baseline then has no file Git reads, and the
     * {@code .gitignore} of the application does not decide which of the copies are committed.
     *
     * @param path the path of a file of the application
     * @return the path of its copy, from {@code .grails/baseline/files}
     */
    static String copyPath(String path) {
        return path.split('/').collect { String step -> READ_BY_GIT.matcher(step).matches() ? step + COPY_SUFFIX : step }.join('/')
    }

    @PackageScope
    static String folded(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)
    }

    /**
     * A file the generator wrote.
     */
    @CompileStatic
    static final class BaselineFile {

        public static final String TEXT = 'text'
        public static final String BINARY = 'binary'

        final String path
        final String sha256
        final String kind
        final boolean executable

        BaselineFile(String path, String sha256, String kind, boolean executable) {
            this.path = path
            this.sha256 = sha256
            this.kind = kind
            this.executable = executable
        }

        boolean isBinary() {
            return kind == BINARY
        }

        @PackageScope
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>()
            map.put('path', path)
            map.put('sha256', sha256)
            map.put('kind', kind)
            map.put('executable', executable)
            return map
        }

        @PackageScope
        static BaselineFile read(StateSection section) {
            // a path is longer than a line of the other files can be
            Object written = section.value('path')
            if (!(written instanceof String) || !isBaselinePath((String) written)) {
                throw section.problem('path', written instanceof String ?
                        "must be the path of a file inside the project, but is '${StateYaml.shown((String) written, StateYaml.MAX_ECHO)}'" : 'is missing')
            }
            BaselineFile file = new BaselineFile(
                    (String) written,
                    section.matching('sha256', ProjectLock.SHA256, 'a SHA-256 digest in hexadecimal'),
                    section.oneOf('kind', [TEXT, BINARY]),
                    section.flag('executable'))
            section.end()
            return file
        }
    }
}
