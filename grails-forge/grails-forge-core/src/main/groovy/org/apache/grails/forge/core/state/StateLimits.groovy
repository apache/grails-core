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

/**
 * How large the state of a project can be. What writes the state and what reads it are held to the
 * same numbers: the generator reads back what it is about to write, so it writes nothing that could
 * not be read, and the reader applies the numbers to the text of a file before any of it is parsed,
 * so a file costs time and memory in proportion to its size and no more.
 *
 * @since 9.0
 */
@CompileStatic
final class StateLimits {

    /** The most bytes the manifest can have */
    public static final int MAX_MANIFEST = 256 * 1024

    /** The most bytes the lock can have */
    public static final int MAX_LOCK = 1024 * 1024

    /** The most bytes the index of the baseline can have */
    public static final int MAX_INDEX = 8 * 1024 * 1024

    /** The most bytes one file of the baseline can have */
    public static final long MAX_BASELINE_FILE = 16L * 1024 * 1024

    /** The most bytes the files of the baseline can have together */
    public static final long MAX_BASELINE = 256L * 1024 * 1024

    /** The most files a baseline can have */
    public static final int MAX_FILES = 20000

    /** The most features a manifest can ask for or leave out, and a lock can have */
    public static final int MAX_FEATURES = 1000

    /** The most commands the features of a project give the command line, all of them together */
    public static final int MAX_ARTEFACTS = 200

    /** The most parameters a manifest or a lock can have */
    public static final int MAX_PARAMETERS = 200

    /** The most repositories a lock can name */
    public static final int MAX_REPOSITORIES = 50

    /** The most characters of a value that is a name, a version or a word */
    public static final int MAX_TEXT = 255

    /** The most characters of the group and the artifact of a feature pack: two parts as long as a repository takes them, and a colon */
    public static final int MAX_COORDINATES = 401

    /** The most characters of the address of a repository, and of the path of a file */
    public static final int MAX_ADDRESS = 2000

    /**
     * The most characters of one line of a file of the state: the longest value, written in quotes
     * with every character of it doubled, and its key.
     */
    public static final int MAX_LINE = 2 * MAX_ADDRESS + 200

    /** How deep the mappings and lists of a file of the state can be */
    public static final int MAX_DEPTH = 6

    /** The most lines the manifest can have: three for a feature */
    public static final int MANIFEST_LINES = 3 * 2 * MAX_FEATURES + MAX_PARAMETERS + 64

    /** The most lines the lock can have: eight for a feature, two for a repository */
    public static final int LOCK_LINES = 8 * MAX_FEATURES + 2 * MAX_REPOSITORIES + MAX_PARAMETERS + 8 * MAX_ARTEFACTS + 64

    /** The most lines the index of the baseline can have: four for a file */
    public static final int INDEX_LINES = 4 * MAX_FILES + 16

    private StateLimits() {
    }
}
