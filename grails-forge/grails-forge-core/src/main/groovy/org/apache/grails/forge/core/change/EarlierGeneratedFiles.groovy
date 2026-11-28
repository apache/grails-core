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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets

/**
 * Files of an application as Grails 7 and 8 generated them, where the whole of such a file is known:
 * a file of an application that was there before it had a record, and that is one of these texts,
 * has nothing of whoever owns the application in it. A feature that changes the file can then give
 * it the content this version generates, as it does for a file of a generated application that was
 * not changed.
 *
 * <p>The texts are compared without the package of the application, which the first line of such a
 * file names, without regard to how the lines end, and without the empty lines and the end of line
 * a file ends with. The specifications hold them to the applications the released versions
 * created.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class EarlierGeneratedFiles {

    private static final String PACKAGE = 'package P'

    // BootStrap.groovy as the base profile of the Grails shell wrote it in Grails 7 and 8, and as Grails Forge of 7 and 8 wrote it
    private static final List<String> BOOTSTRAP = [
            PACKAGE + '\n\nclass BootStrap {\n\n    def init = {\n    }\n\n    def destroy = {\n    }\n\n}',
            PACKAGE + '\n\nclass BootStrap {\n    def init = {\n    }\n\n    def destroy = {\n    }\n\n}']

    private EarlierGeneratedFiles() {
    }

    /**
     * @param path the path of a file of an application, with {@code /} between its steps
     * @param content the content the application has for it
     * @return whether the file is, byte for byte but for its package and how its lines end, what an
     * earlier version of Grails generated at that path; {@code false} for a path no text is known of
     */
    static boolean isAsGenerated(String path, byte[] content) {
        if (content == null || !(path ==~ /grails-app\/init\/(.+\/)?BootStrap\.groovy/)) {
            return false
        }
        String text = new String(content, StandardCharsets.UTF_8).replace('\r\n', '\n').replaceFirst(/\n+$/, '')
                .replaceFirst(/\Apackage [A-Za-z_][A-Za-z0-9_.]*\n/, PACKAGE + '\n')
        return BOOTSTRAP.contains(text)
    }
}
