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
package org.apache.grails.forge.core.pack

import java.nio.file.Files
import java.nio.file.Path
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

/**
 * A directory that is given to the generator as a pack, as {@code --feature-pack-dir} gives it, can be
 * a link to the directory of the pack: the generator reads the directory it was told to read, and
 * whoever names the link chose where it leads. What is in the directory cannot be a link, see
 * {@code PackGenerationSpec}. The tasks of the Grails Gradle plugin are stricter and refuse a
 * {@code src/main/forge} that is a link, since what a build reads through a link is no input of it.
 */
class PackDirectoryLinkSpec extends Specification {

    @TempDir
    Path directory

    @IgnoreIf({ os.windows })
    void "a pack directory that is a link is read as the directory it leads to, and a link in it is still refused"() {
        given:
        Path real = Files.createDirectories(directory.resolve('real/templates')).parent
        Files.writeString(real.resolve('feature.yml'), 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                'files:\n  - from: a.txt\n    to: a.txt\n')
        Files.writeString(real.resolve('templates/a.txt'), 'of the pack')
        Path link = Files.createSymbolicLink(directory.resolve('link'), real)

        when:
        Pack pack = new PackLoader().fromDirectory(link)

        then:
        pack.descriptor.name == 'acme'
        pack.contentSha256 == new PackLoader().fromDirectory(real).contentSha256

        when: 'a file of the pack is a link'
        Files.writeString(directory.resolve('outside.txt'), 'outside')
        Files.delete(real.resolve('templates/a.txt'))
        Files.createSymbolicLink(real.resolve('templates/a.txt'), directory.resolve('outside.txt'))
        new PackLoader().fromDirectory(link)

        then:
        PackDescriptorException e = thrown()
        e.problems == ["files[0].from: 'a.txt' is not a file under templates of the pack, or is a link or is reached through one"]
    }
}
