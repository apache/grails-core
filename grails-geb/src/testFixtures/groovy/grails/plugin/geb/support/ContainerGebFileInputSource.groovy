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

package grails.plugin.geb.support

/**
 * A File implementation specifically for assigning to the Geb FileInput module when using ContainerGebSpec.
 * This will normalize the path for cross-platform compatibility (Windows paths with in Linux containers).
 *
 * @author Mattias Reichel
 * @since 4.2
 */
class ContainerGebFileInputSource extends File {

    String normalizedPath

    ContainerGebFileInputSource(String path) {
        super(path)
        normalizedPath = normalizePath(path)
    }

    @Override
    String getAbsolutePath() {
        return normalizedPath
    }

    /**
     * Normalizes the path for cross-platform compatibility (Windows paths used in Linux containers).
     *
     * @param path the original file path
     * @return the normalized path with forward slashes
     */
    protected String normalizePath(String path) {
        // Normalize separator to forward slash
        path.replace(separatorChar, (char) '/')
    }
}
