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
package org.grails.io.watch

import groovy.transform.CompileStatic

/**
 * A {@link org.grails.io.watch.DirectoryWatcher.FileChangeListener} that only fires for specific extension or list of extensions
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class FileExtensionFileChangeListener implements DirectoryWatcher.FileChangeListener {

    final List<String> extensions

    FileExtensionFileChangeListener(List<String> extensions) {
        this.extensions = extensions
    }

    @Override
    final void onChange(File file) {
        if (file && extensions.any() { String ext -> file.name.endsWith(ext) }) {
            onChange(file, extensions)
        }
    }

    abstract void onChange(File file, List<String> extensions)

    @Override
    final void onNew(File file) {
        if (file && extensions.any() { String ext -> file.name.endsWith(ext) }) {
            onNew(file, extensions)
        }
    }

    abstract void onNew(File file, List<String> extensions)
}
