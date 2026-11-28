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
package org.apache.grails.forge.cli.console

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.UserPrincipal
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Whose a file is on Windows, where the owner of what an elevated administrator creates is the group
 * of the administrators and not the user: {@link ProcessUser#owns} with what Windows says given by
 * the specification, which runs on every system, and with what Windows itself says where the
 * specification runs on it.
 */
class ProcessUserOnWindowsSpec extends Specification {

    static final UserPrincipal ME = new Owner('S-1-5-21-1-2-3-1001', 'HOST\\me')
    static final UserPrincipal SOMEONE = new Owner('S-1-5-21-1-2-3-1002', 'HOST\\someone')
    // the name is in the language of the system, and is not what is compared
    static final GroupPrincipal ADMINISTRATORS = new Group(ProcessUser.ADMINISTRATORS, 'VORDEFINIERT\\Administratoren')
    static final GroupPrincipal USERS = new Group('S-1-5-32-545', 'VORDEFINIERT\\Benutzer')
    static final GroupPrincipal EVERYBODY = new Group('S-1-1-0', '\\Everyone')

    @TempDir
    Path dir

    void cleanup() {
        ProcessUser.setCurrent(null)
        ConsoleProject.setOwnedByUser(null)
    }

    @Unroll
    void "a file is the user's own on Windows: #title"() {
        given:
        ProcessUser user = ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: groups, created: created, owner: owner))

        expect:
        user.owns(dir) == own

        where:
        title                                                                                         | owner          | groups                                     | created        || own
        'the user owns it'                                                                            | ME             | []                                         | ME             || true
        'the user owns it, in an elevated process'                                                    | ME             | [ProcessUser.ADMINISTRATORS]               | ADMINISTRATORS || true
        'the administrators own it, and the process is that of an administrator and elevated'         | ADMINISTRATORS | ['S-1-1-0', ProcessUser.ADMINISTRATORS]    | ADMINISTRATORS || true
        'the administrators own it, and the user is an administrator in a process that is not elevated' | ADMINISTRATORS | ['S-1-1-0', ProcessUser.ADMINISTRATORS]    | ME             || false
        'the administrators own it, and the user is no administrator'                                 | ADMINISTRATORS | ['S-1-1-0', 'S-1-5-32-545']                | ME             || false
        'the administrators own it, and the groups of the process cannot be read'                     | ADMINISTRATORS | []                                         | ADMINISTRATORS || false
        'the administrators own it, and whom the files of the process are given to cannot be told'    | ADMINISTRATORS | [ProcessUser.ADMINISTRATORS]               | null           || false
        'another user owns it'                                                                        | SOMEONE        | [ProcessUser.ADMINISTRATORS]               | ADMINISTRATORS || false
        'another user owns it, and the files of the process could not be told from theirs'            | SOMEONE        | [ProcessUser.ADMINISTRATORS]               | SOMEONE        || false
        'another group owns it than the one the files of the process are given to'                    | USERS          | [ProcessUser.ADMINISTRATORS, 'S-1-5-32-545'] | ADMINISTRATORS || false
        'everybody owns it, as on a file system that keeps no owner'                                  | EVERYBODY      | [ProcessUser.ADMINISTRATORS]               | ADMINISTRATORS || false
        'its owner cannot be read'                                                                    | null           | [ProcessUser.ADMINISTRATORS]               | ADMINISTRATORS || false
    }

    void "an owner that the system refuses to give is not the user"() {
        given:
        ProcessUser user = ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: [ProcessUser.ADMINISTRATORS], created: ADMINISTRATORS, refused: true))

        expect:
        !user.owns(dir)
        user.comparedWith(dir) == ''
    }

    void "an owner is compared by its name only where the system finds no principal for the user"() {
        expect:
        ProcessUser.onWindows('HOST\\me', new Said(user: null, groups: [], created: null, owner: new Owner('S-1-5-21-9', 'host\\ME'))).owns(dir)
        !ProcessUser.onWindows('HOST\\me', new Said(user: null, groups: [], created: null, owner: SOMEONE)).owns(dir)

        and: 'a principal that was found decides, whatever the names are'
        !ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: [], created: ME, owner: new Owner('S-1-5-21-9', 'HOST\\me'))).owns(dir)
    }

    void "the line that refuses a file of a group says what the process is and whom its files are given to"() {
        given:
        ProcessUser user = ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: ['S-1-1-0', ProcessUser.ADMINISTRATORS], created: ME, owner: ADMINISTRATORS))

        expect:
        user.comparedWith(dir) == ' (HOST\\me). What a group owns is the own of an administrator in an elevated process only: ' +
                'this process has the group of the administrators (S-1-5-32-544) in its access token, and the files it creates are owned by HOST\\me'

        and: 'for a file of another user the name of the user is all there is to say'
        ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: [], created: ME, owner: SOMEONE)).comparedWith(dir) == ' (HOST\\me)'
    }

    void "a project above the current directory that the administrators own is used in the elevated process of an administrator, and in no other"() {
        given:
        Path project = Files.createDirectories(dir.resolve('app'))
        Files.createDirectories(project.resolve('grails-app'))
        Path below = Files.createDirectories(project.resolve('src/main'))

        when: 'the process is elevated'
        ProcessUser.setCurrent(ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: [ProcessUser.ADMINISTRATORS], created: ADMINISTRATORS, owner: ADMINISTRATORS)))
        ConsoleProject.Search elevated = ConsoleProject.search(below)

        then:
        elevated.project != null
        elevated.project.directory == project.toAbsolutePath().normalize()

        when: 'the process is not'
        ProcessUser.setCurrent(ProcessUser.onWindows('HOST\\me', new Said(user: ME, groups: [ProcessUser.ADMINISTRATORS], created: ME, owner: ADMINISTRATORS)))
        ConsoleProject.Search plain = ConsoleProject.search(below)

        then:
        plain.project == null
        plain.untrusted.contains('not by the user who runs the command (HOST\\me). What a group owns is the own of an administrator in an elevated process only: ' +
                'this process has the group of the administrators (S-1-5-32-544) in its access token, and the files it creates are owned by HOST\\me')
    }

    @Requires({ os.windows })
    void "on Windows the user of the process is established by name, and what the process creates is its own"() {
        given:
        ProcessUser user = ProcessUser.establish([])
        Path file = Files.createFile(dir.resolve('created.txt'))
        Path directory = Files.createDirectory(dir.resolve('created'))

        expect:
        user.known
        user.source == ProcessUser.NAME
        assert user.owns(file) : "a file this process created is owned by ${Files.getOwner(file)} and is not its own${user.comparedWith(file)}"
        assert user.owns(directory) : "a directory this process created is owned by ${Files.getOwner(directory)} and is not its own${user.comparedWith(directory)}"
    }

    @Requires({ os.windows })
    void "on Windows what the system itself owns is not the user's own"() {
        given:
        ProcessUser user = ProcessUser.establish([])
        // owned by the service that installs Windows, on every system
        Path system = Path.of(System.getenv('SystemRoot') ?: 'C:\\Windows', 'System32', 'kernel32.dll')

        expect:
        assert !user.owns(system) : "${system} is owned by ${Files.getOwner(system)} and was taken for the user's own"
        user.comparedWith(system).startsWith(' (')
    }

    static class Said implements ProcessUser.Windows {

        UserPrincipal user
        Collection<String> groups
        UserPrincipal created
        UserPrincipal owner
        boolean refused

        @Override
        UserPrincipal user() {
            return user
        }

        @Override
        Collection<String> groups() {
            return groups
        }

        @Override
        UserPrincipal ownerOfCreated() {
            return created
        }

        @Override
        UserPrincipal ownerOf(Path path) throws IOException {
            if (refused) {
                throw new IOException('Access is denied')
            }
            return owner
        }
    }

    // equal by the security identifier, as the principals of the file system of Windows are
    static class Owner implements UserPrincipal {

        final String sid
        final String name

        Owner(String sid, String name) {
            this.sid = sid
            this.name = name
        }

        @Override
        String getName() {
            return name
        }

        @Override
        boolean equals(Object other) {
            return other instanceof Owner && ((Owner) other).sid == sid
        }

        @Override
        int hashCode() {
            return sid.hashCode()
        }

        @Override
        String toString() {
            return name
        }
    }

    static class Group extends Owner implements GroupPrincipal {

        Group(String sid, String name) {
            super(sid, name)
        }
    }
}
