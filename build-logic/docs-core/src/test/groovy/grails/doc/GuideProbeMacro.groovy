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
package grails.doc

import org.radeox.macro.BaseMacro
import org.radeox.macro.parameter.MacroParameter

/**
 * A macro {@link UserGuideBuilderSpec} registers by class name, to show that a name is enough -
 * a macro instance cannot be handed to the process that renders the guide.
 */
class GuideProbeMacro extends BaseMacro {

    static boolean instantiated = false

    GuideProbeMacro() {
        instantiated = true
    }

    @Override
    String getName() { 'guideProbe' }

    @Override
    void execute(Writer out, MacroParameter params) {
        out << '<span class="guide-probe"></span>'
    }
}
