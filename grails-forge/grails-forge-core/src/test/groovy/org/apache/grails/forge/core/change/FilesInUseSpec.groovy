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

import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A file of a project that another program has open for a moment, as an editor or a scanner has it on
 * Windows, where the system then refuses to replace or remove it: what is tried again, for how long,
 * and what fails at once. What the system refuses is given by the specification, so it runs on every
 * system; two features have Windows itself refuse.
 */
class FilesInUseSpec extends Specification {

    static final String IN_USE = 'The process cannot access the file because it is being used by another process'

    @TempDir
    Path dir

    List<Long> waited = []

    void setup() {
        FilesInUse.set(true, { long millis -> waited.add(millis) } as FilesInUse.Sleeper)
    }

    void cleanup() {
        FilesInUse.set(null, null)
    }

    @Unroll
    void "what Windows refuses for a file that is in use is tried again until it is done: #refusals times #type.simpleName"() {
        given:
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        int tries = 0

        when:
        FilesInUse.attempt(file) {
            if (tries++ < refusals) {
                throw type.getConstructor(String, String, String).newInstance(file.toString(), null, IN_USE)
            }
            Files.delete(file)
        }

        then:
        !Files.exists(file)
        tries == refusals + 1
        waited == FilesInUse.PAUSES.take(refusals)

        where:
        refusals | type
        0        | FileSystemException
        1        | FileSystemException
        3        | AccessDeniedException
        5        | FileSystemException
    }

    void "a file that stays in use is a failure after a second and a half, with what the system said last"() {
        given:
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        int tries = 0

        when:
        FilesInUse.attempt(file) {
            throw new FileSystemException(file.toString(), null, "${IN_USE} (${++tries})")
        }

        then:
        FileSystemException e = thrown()
        e.reason == "${IN_USE} (6)"
        tries == 6
        waited == [50L, 100L, 200L, 400L, 800L]
        waited.sum() == 1550L
    }

    @Unroll
    void "what cannot be the use of a file by another program fails at once: #title"() {
        given:
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        int tries = 0

        when:
        FilesInUse.attempt(file) {
            tries++
            throw failure
        }

        then:
        IOException e = thrown()
        e.is(failure)
        tries == 1
        waited == []

        where:
        title                              | failure
        'the file is not there'            | new NoSuchFileException('build.gradle')
        'there is a file already'          | new FileAlreadyExistsException('build.gradle')
        'the directory is not empty'       | new DirectoryNotEmptyException('build.gradle')
        'another failure of reading it'    | new IOException('The device is not ready')
    }

    @IgnoreIf({ System.getProperty('user.name') == 'root' })
    void "a file that cannot be written to is not waited for: that it is refused is no moment of another program"() {
        given:
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        file.toFile().setWritable(false)
        int tries = 0

        expect:
        !FilesInUse.mayBeInUse(new AccessDeniedException(file.toString()), file)

        when:
        FilesInUse.attempt(file) {
            tries++
            throw new AccessDeniedException(file.toString())
        }

        then:
        thrown(AccessDeniedException)
        tries == 1
        waited == []

        cleanup:
        file.toFile().setWritable(true)
    }

    void "for a file that is not there yet, the directory it is to be in is the one that has to be writable"() {
        expect:
        FilesInUse.mayBeInUse(new AccessDeniedException('new.gradle'), dir.resolve('new.gradle'))
        !FilesInUse.mayBeInUse(new AccessDeniedException('new.gradle'), dir.resolve('no-such-directory/new.gradle'))
    }

    void "where the system replaces and removes a file that is open, nothing is tried twice"() {
        given:
        FilesInUse.set(false, { long millis -> waited.add(millis) } as FilesInUse.Sleeper)
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        int tries = 0

        when:
        FilesInUse.attempt(file) {
            tries++
            throw new FileSystemException(file.toString(), null, IN_USE)
        }

        then:
        thrown(FileSystemException)
        tries == 1
        waited == []

        when: 'what the system is decides it where no specification does'
        FilesInUse.set(null, null)

        then:
        FilesInUse.mayBeInUse(new FileSystemException(file.toString(), null, IN_USE), file) == (File.separatorChar == ('\\' as char))
    }

    void "a command that is cancelled while it waits fails with what the system said, and stays cancelled"() {
        given:
        FilesInUse.set(true, { long millis -> throw new InterruptedException() } as FilesInUse.Sleeper)
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')

        when:
        FilesInUse.attempt(file) {
            throw new FileSystemException(file.toString(), null, IN_USE)
        }

        then:
        thrown(FileSystemException)
        Thread.interrupted()
    }

    @Requires({ os.windows })
    void "on Windows a file that a program has open is removed once the program has closed it"() {
        given: 'a program that has the file open as one that reads it has, and closes it while the first try waits to be made again'
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        RandomAccessFile open = new RandomAccessFile(file.toFile(), 'r')
        closedInTheFirstPause(open)
        IOException atOnce = null
        try {
            Files.delete(file)
        } catch (IOException e) {
            atOnce = e
        }

        expect: 'the system refuses while it is open, with what is tried again'
        assert atOnce != null: 'Windows removed a file that a program had open'
        assert FilesInUse.mayBeInUse(atOnce, file): "what Windows said is not tried again: ${atOnce.getClass().name}: ${atOnce.message}"

        when:
        FilesInUse.attempt(file) {
            Files.delete(file)
        }

        then: 'a try after the first has done it'
        noExceptionThrown()
        !Files.exists(file)
        !waited.empty

        cleanup:
        open?.close()
    }

    @Requires({ os.windows })
    void "on Windows a file that a program has open is replaced once the program has closed it"() {
        given: 'a program that has the file open, and closes it while the first try waits to be made again'
        Path file = Files.writeString(dir.resolve('build.gradle'), 'plugins {}\n')
        Path written = Files.writeString(dir.resolve('build.gradle.new'), 'plugins { id "groovy" }\n')
        RandomAccessFile open = new RandomAccessFile(file.toFile(), 'r')
        closedInTheFirstPause(open)
        IOException atOnce = null
        try {
            Files.move(written, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (IOException e) {
            atOnce = e
        }

        expect:
        assert atOnce != null: 'Windows replaced a file that a program had open'
        assert FilesInUse.mayBeInUse(atOnce, file): "what Windows said is not tried again: ${atOnce.getClass().name}: ${atOnce.message}"

        when:
        FilesInUse.attempt(file) {
            Files.move(written, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        then: 'a try after the first has done it'
        noExceptionThrown()
        file.toFile().text == 'plugins { id "groovy" }\n'
        !waited.empty

        cleanup:
        open?.close()
    }

    // As the system is, and with the waits of the thread; the program closes the file in the first of them, so
    // that it is closed before the tries are over however slow the machine is.
    private void closedInTheFirstPause(RandomAccessFile open) {
        FilesInUse.set(null, { long millis ->
            waited.add(millis)
            open.close()
            Thread.sleep(millis)
        } as FilesInUse.Sleeper)
    }
}
