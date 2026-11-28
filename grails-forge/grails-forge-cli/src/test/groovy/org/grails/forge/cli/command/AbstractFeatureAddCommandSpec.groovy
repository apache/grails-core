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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec

/**
 * What the specs of {@code feature add} and {@code project regenerate} share: the standard output and
 * error of the commands, a home directory of the test for the journals of the changes, applications
 * that the create commands make, and the whole of a project as one value to compare.
 */
abstract class AbstractFeatureAddCommandSpec extends CommandSpec implements CommandFixture {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    /** Where an application is created to compare with */
    File with

    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        with = Files.createTempDirectory('grailsforgewith').toFile()
        // the journal of a change is kept in the home directory of whoever runs the command
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        with.deleteDir()
        home.deleteDir()
    }

    protected void reset() {
        out.reset()
        err.reset()
    }

    protected int add(String... arguments) {
        return CliRunner.run(FeatureAddCommand, beanContext, arguments)
    }

    protected int regenerate(String... arguments) {
        return CliRunner.run(ProjectRegenerateCommand, beanContext, arguments)
    }

    /**
     * Creates an application with a create command. What the command says is not kept: the output of
     * the command that is run next is all there is.
     */
    protected int create(File directory, Class command, List<String> arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return CliRunner.run(command, beanContext, (['com.example.shop', '--inplace'] + arguments) as String[])
        } finally {
            System.setProperty('user.dir', current)
            reset()
        }
    }

    /**
     * Everything of a project: every directory with its mode, and every file with the digest of its
     * content and its mode, a link as a link. The directory of the changes and the lock in it are
     * left out, because a project has them once a feature was added to it and not before.
     */
    protected static Map<String, String> tree(File project) {
        Map<String, String> entries = new TreeMap<>()
        boolean posix = project.toPath().fileSystem.supportedFileAttributeViews().contains('posix')
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.each { Path path ->
                String name = project.toPath().relativize(path).join('/')
                if (name.isEmpty() || name == '.grails/transactions' || name == '.grails/transactions/lock') {
                    return
                }
                String mode = posix ? PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)) : ''
                if (Files.isSymbolicLink(path)) {
                    entries.put(name, "link to ${Files.readSymbolicLink(path)}".toString())
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.put(name, "directory ${mode}".toString())
                } else {
                    entries.put(name, "${sha(Files.readAllBytes(path))} ${mode}".toString())
                }
            }
        }
        return entries
    }

    protected static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}
