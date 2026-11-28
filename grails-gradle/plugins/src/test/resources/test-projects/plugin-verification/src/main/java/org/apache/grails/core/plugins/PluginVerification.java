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
package org.apache.grails.core.plugins;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Stands in for the class of Grails that checks the plugins: fails when the class path has fail.txt, and reports
 * whether it has marker.txt.
 */
public class PluginVerification {

    public static void main(String[] args) throws Exception {
        List<String> environments = List.of(args).subList(2, args.length);
        if (PluginVerification.class.getResource("/fail.txt") != null) {
            Files.writeString(Path.of(args[1]), "Grails plug-in [needy] cannot be loaded in " + environments);
        }
        else {
            String found = PluginVerification.class.getResource("/marker.txt") != null ? "marker\n" : "";
            Files.writeString(Path.of(args[0]), String.join(",", environments) + "\n" + found);
        }
    }
}
