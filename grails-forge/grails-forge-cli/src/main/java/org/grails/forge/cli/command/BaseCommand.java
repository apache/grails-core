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
package org.grails.forge.cli.command;

import jakarta.annotation.Nullable;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.cli.CommonOptionsMixin;
import org.grails.forge.io.ConsoleOutput;
import picocli.CommandLine;

import java.util.Locale;

public class BaseCommand implements ConsoleOutput {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    protected CommonOptionsMixin commonOptions = new CommonOptionsMixin();

    public void out(String message) {
        spec.commandLine().getOut().println(CommandLine.Help.Ansi.AUTO.string(message));
    }

    public void err(String message) {
        spec.commandLine().getErr().println(CommandLine.Help.Ansi.AUTO.string("@|bold,red | Error|@ " + message));
    }

    public void warning(String message) {
        spec.commandLine().getOut().println(CommandLine.Help.Ansi.AUTO.string("@|bold,red | Warning|@ " + message));
    }

    @Override
    public void green(String message) {
        spec.commandLine().getOut().println(CommandLine.Help.Ansi.AUTO.string("@|bold,green " + message + "|@"));
    }

    @Override
    public void red(String message) {
        spec.commandLine().getOut().println(CommandLine.Help.Ansi.AUTO.string("@|bold,red " + message + "|@"));
    }

    public boolean showStacktrace() {
        return commonOptions.showStacktrace;
    }

    public boolean verbose() {
        return commonOptions.verbose;
    }

    @Nullable
    public OperatingSystem getOperatingSystem() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        if (osName.contains("mac") || osName.contains("darwin")) {
            final String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
            if (osArch.equals("aarch64")) {
                return OperatingSystem.MACOS_ARCH64;
            } else {
                return OperatingSystem.MACOS;
            }
        } else if (osName.contains("linux")) {
            return OperatingSystem.LINUX;
        } else if (osName.contains("win")) {
            return OperatingSystem.WINDOWS;
        } else if (osName.contains("sunos") || osName.contains("solaris")) {
            return OperatingSystem.SOLARIS;
        } else {
            return null;
        }
    }

}
