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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import org.grails.forge.feature.FeatureSelectionException

/**
 * What {@code --preset} of a create command names, read from what was typed, in the forms a feature
 * pack is given in: the coordinates of the artifact that has the preset, {@code group:artifact:version};
 * a jar or a directory, by a path; or the name of a preset in a feature catalog, or its full name
 * {@code group:artifact#name}.
 *
 * <p>A path is told from a name by its form and not by what is on the disk: it has a {@code /} (or a
 * {@code \}) in it, ends with {@code .jar}, or is {@code .} or {@code ..}. A directory of the current
 * directory is therefore given as {@code ./acme-preset}, and a directory whose name is that of a preset
 * of a catalog does not take the place of the preset.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PresetReference {

    /** How a preset is given */
    enum Form {
        /** The coordinates of the artifact that has the preset */
        COORDINATES,
        /** The jar that has the preset */
        JAR,
        /** The directory that has the preset ({@code feature.yml} and its {@code templates}) */
        DIRECTORY,
        /** The name of a preset in a feature catalog, or its full name */
        NAME
    }

    final Form form

    /** What was typed */
    final String given

    /** The coordinates, of {@link Form#COORDINATES} */
    final PackArtifacts.Coordinates coordinates

    /** The absolute path, of {@link Form#JAR} and {@link Form#DIRECTORY} */
    final Path path

    private PresetReference(Form form, String given, PackArtifacts.Coordinates coordinates, Path path) {
        this.form = form
        this.given = given
        this.coordinates = coordinates
        this.path = path
    }

    /**
     * @param given what was typed after {@code --preset}
     * @return what it names
     * @throws IllegalArgumentException when it names nothing a preset can be taken from, with what the
     * forms are
     */
    static PresetReference of(String given) {
        String shown = FeatureSelectionException.shown(given ?: '')
        if (given == null || given.isBlank()) {
            throw new IllegalArgumentException('--preset names the preset an application is made from, and is empty')
        }
        if (isPath(given)) {
            Path path
            try {
                path = Path.of(given).toAbsolutePath().normalize()
            } catch (InvalidPathException ignored) {
                throw new IllegalArgumentException("--preset ${shown} is not a path on this computer")
            }
            if (Files.isDirectory(path)) {
                return new PresetReference(Form.DIRECTORY, given, null, path)
            }
            if (Files.isRegularFile(path)) {
                return new PresetReference(Form.JAR, given, null, path)
            }
            throw new IllegalArgumentException("--preset ${shown} is a path, and there is no directory or file there")
        }
        if (PackCatalogs.isId(given)) {
            return new PresetReference(Form.NAME, given, null, null)
        }
        if (given.contains(':')) {
            return new PresetReference(Form.COORDINATES, given, PackArtifacts.coordinates(given), null)
        }
        if (PackCatalogs.isName(given)) {
            return new PresetReference(Form.NAME, given, null, null)
        }
        throw new IllegalArgumentException("--preset ${shown} is none of the forms a preset is given in: the coordinates of its artifact, " +
                'group:artifact:version; a jar or a directory, by a path with a / in it, such as ./acme-preset; or the name of a preset in a ' +
                'feature catalog')
    }

    private static boolean isPath(String given) {
        return given.contains('/') || given.contains('\\') || given.toLowerCase(Locale.ROOT).endsWith('.jar') || given == '.' || given == '..'
    }

    @Override
    String toString() {
        return given
    }
}
