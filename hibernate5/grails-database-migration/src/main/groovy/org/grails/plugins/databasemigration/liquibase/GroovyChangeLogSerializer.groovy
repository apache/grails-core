/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic
import liquibase.changelog.ChangeLogChild
import liquibase.changelog.ChangeSet
import liquibase.serializer.ChangeLogSerializer
import liquibase.serializer.LiquibaseSerializable
import liquibase.serializer.core.xml.XMLChangeLogSerializer

@CompileStatic
class GroovyChangeLogSerializer implements ChangeLogSerializer {

    private XMLChangeLogSerializer xmlChangeLogSerializer = new XMLChangeLogSerializer()

    @Override
    def <T extends ChangeLogChild> void write(List<T> changesets, OutputStream out) throws IOException {
        def xmlOutputStrem = new ByteArrayOutputStream()
        xmlChangeLogSerializer.write(changesets, xmlOutputStrem)
        out << ChangelogXml2Groovy.convert(xmlOutputStrem.toString())
    }

    @Override
    void append(ChangeSet changeSet, File changeLogFile) throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    String[] getValidFileExtensions() {
        ['groovy'] as String[]
    }

    @Override
    String serialize(LiquibaseSerializable object, boolean pretty) {
        throw new UnsupportedOperationException()
    }

    @Override
    int getPriority() {
        return 0
    }
}
