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
package org.apache.grails.forge.cli.artefact

import groovy.transform.CompileStatic
import java.text.Normalizer
import javax.lang.model.SourceVersion
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.grails.forge.application.Project
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.util.NameUtils

/**
 * The name of a class a command is asked to create, held to what the name of a new class has to be.
 * The name is given on the command line and becomes directories and the name of a file, so it is a
 * name of Java and nothing else: no path, no character that is not shown, and no step a file system
 * could not have.
 *
 * @since 9.0
 */
@CompileStatic
final class ArtefactName {

    /** The most characters a name can have, its package included */
    public static final int MAX_NAME = 200

    private static final Set<String> FILE_ENDINGS = ['groovy', 'java'] as Set<String>

    private ArtefactName() {
    }

    /**
     * @param given the name as it was given: {@code Book}, {@code book}, {@code book-store} or
     * {@code org.example.Book}
     * @param defaultPackage the package of a name that has none, which can be missing
     * @param convention what the kind of class adds to the name, such as {@code Controller}, which a
     * name that ends with it already is given without; or null
     * @return the names of the class: {@code className} is the name without the convention
     * @throws IllegalArgumentException when the name cannot be the name of a class, with a message of one line
     */
    static Project parse(String given, String defaultPackage, String convention) {
        return parse(given, defaultPackage, convention, convention ?: '')
    }

    /**
     * @param added what the names of the files of the class have after the name of the class, for a
     * command that adds it whatever the name ends with: {@code Controller} for a class that is
     * written to {@code BookController.groovy} and {@code BookControllerSpec.groovy}
     * @see #parse(String, String, String)
     */
    static Project parse(String given, String defaultPackage, String convention, String added) {
        if (given == null || given.isEmpty()) {
            throw new IllegalArgumentException('A name is needed')
        }
        String shown = FeatureSelectionException.shown(given)
        if (given.length() > MAX_NAME) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: it has more than ${MAX_NAME} characters")
        }
        if (PackDescriptorParser.hasUnsafeCharacter(given)) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: it has a character that is not shown, such as a control character")
        }
        // source files are written in the composed form, and a file system that lists a name in another
        // form than it was given would have the class and its file differ
        if (!Normalizer.isNormalized(given, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: a letter of it is written as a letter and a mark that follows it. " +
                    'Write the letter as one character (the form NFC of Unicode)')
        }
        if (given.contains('/') || given.contains('\\') || given.contains(':')) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: it is a path. A package is given with dots, as in org.example.Book")
        }
        int dot = given.lastIndexOf('.')
        String packageName = dot < 0 ? defaultPackage : given.substring(0, dot)
        String simple = dot < 0 ? given : given.substring(dot + 1)
        if (dot == 0 || simple.isEmpty()) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: it starts or ends with a dot")
        }
        // in lower case it is the ending of a file that was typed with the name; Groovy and Java are names of classes
        if (dot > 0 && simple in FILE_ENDINGS) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: it is the name of a file. The name of the class is given, without .${simple}")
        }
        // each word that a hyphen sets apart starts with a capital: book-store is BookStore
        StringBuilder capitals = new StringBuilder()
        for (String word : simple.split('-', -1)) {
            if (word.isEmpty()) {
                throw new IllegalArgumentException("\"${shown}\" is not a name for a class: a hyphen stands between two words of a name")
            }
            int first = word.codePointAt(0)
            // the capital of the one letter, which not every letter has: the capital of a sharp s is two letters
            int capital = Character.toUpperCase(first)
            if (Character.isLowerCase(capital)) {
                throw new IllegalArgumentException("\"${shown}\" is not a name for a class: ${FeatureSelectionException.shown(word)} starts with a letter that has no capital")
            }
            capitals.appendCodePoint(capital).append(word.substring(Character.charCount(first)))
        }
        String className = capitals.toString()
        if (convention && className.length() > convention.length() && className.endsWith(convention)) {
            className = className.substring(0, className.length() - convention.length())
        }
        if (!SourceVersion.isIdentifier(className) || SourceVersion.isKeyword(className)) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: ${FeatureSelectionException.shown(className)} is not an identifier of Java")
        }
        if (packageName && !SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: ${FeatureSelectionException.shown(packageName)} is not a package of Java")
        }
        // the package is directories and the name is a file: neither is one Windows could not have. It is
        // the names that are written that count: Con is no name for a class of its own file, and is one
        // for a controller, whose file is ConController.groovy
        List<String> steps = (packageName ? packageName.split('\\.').toList() : []) + [className + added]
        String device = steps.find { String step -> PackDescriptorParser.isDeviceName(step) }
        if (device != null) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: ${device} is the name of a device on Windows, " +
                    'so the file could not be checked out there')
        }
        if (!PackDescriptorParser.isPathBelowRoot(steps.join('/'))) {
            throw new IllegalArgumentException("\"${shown}\" is not a name for a class: not every file system takes it as the name of a file")
        }
        return new Project(packageName ?: null, packageName ? packageName.replace('.', '/') : '', className,
                NameUtils.getNaturalName(className), NameUtils.getPropertyName(className), className)
    }
}
