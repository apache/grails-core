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

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.grails.forge.util.VersionInfo
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The files of a Grails wrapper that are in use while {@code update-wrapper} runs, as two of them are
 * on Windows when the command was typed to the wrapper: what {@link WrapperFiles} leaves in the
 * directory, and that the wrapper it leaves completes the update when it is next run. What the system
 * refuses and what the command interpreter is reading are given by the specification, so it runs on
 * every system; {@code WrapperUpgradeSpec} of {@code grails-cli} runs the batch files on Windows.
 */
class WrapperInUseSpec extends Specification {

    static final String JAR_IN_USE = 'The process cannot access the file because it is being used by another process'

    @TempDir
    File dir

    File project

    void setup() {
        project = new File(dir, 'shop').canonicalFile
        new File(project, 'grails-app').mkdirs()
        new File(project, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        UpdateWrapperCommandSpec.earlierWrapper(project, '8.1.0')
    }

    void "the scripts of this version move a jar that is written beside the one in use over it before they start Java, and end by a label"() {
        given:
        List<String> batch = text('grailsw.bat').readLines()
        List<String> script = text('grailsw').readLines()

        expect: 'in the batch file, before the variables are set and so before Java is looked for'
        int moves = batch.indexOf('if exist "%~dp0grails-wrapper.jar.new" move /y "%~dp0grails-wrapper.jar.new" "%~dp0grails-wrapper.jar" >NUL 2>&1')
        moves > 0
        moves < batch.findIndexOf { String line -> line.startsWith('setlocal') }
        text('grailsw.bat').contains('\r\n')

        and: 'what the batch file does once Java has ended it finds by a label, so it can be replaced while it is read'
        batch.count { String line -> line.contains('grails.init.Start') } == 1
        batch.find { String line -> line.contains('grails.init.Start') }.endsWith('grails.init.Start %* & call :exitWithErrorLevel & goto exitWithErrorLevel')
        batch.contains(':exitWithErrorLevel')

        and: 'in the other script, which a shell of Git for Windows runs'
        int moved = script.indexOf('    mv -f "$APP_HOME/grails-wrapper.jar.new" "$APP_HOME/grails-wrapper.jar" 2>/dev/null || :')
        moved > script.indexOf('CLASSPATH=$APP_HOME/grails-wrapper.jar')
        moved < script.findIndexOf { String line -> line.startsWith('exec ') }
    }

    void "a jar the system refuses to replace is written beside it, and the wrapper replaces it when it is next run"() {
        given:
        byte[] jarBefore = new File(project, 'grails-wrapper.jar').bytes
        WrapperFiles files = new WrapperFiles(project.toPath())
        files.mover = refusingTheJar()

        when:
        WrapperFiles.Result result = files.update()

        then:
        !result.complete
        result.settled
        result.replaced == ['grailsw', 'grailsw.bat']
        result.pending.keySet() == ['grails-wrapper.jar'] as Set
        result.beside.isEmpty() && result.failed.isEmpty()
        result.left().isEmpty()
        new File(project, 'grails-wrapper.jar').bytes == jarBefore
        new File(project, 'grails-wrapper.jar.new').bytes == generated('grails-wrapper.jar')

        and: 'one line says all of it'
        result.done() == "The Grails wrapper of the project in ${project.path} was that of Grails 8.1.0 and is being replaced by that of Grails ${VersionInfo.grailsVersion}: " +
                'grailsw, grailsw.bat were replaced, and grails-wrapper.jar is in use by the wrapper that runs this command and is replaced by the wrapper itself when it is next run. ' +
                'Run the wrapper once more, and then commit grailsw, grailsw.bat, grails-wrapper.jar; the files named .new are gone by then.'

        when: 'the command is run again before the wrapper was: the jar is still in use'
        WrapperFiles second = new WrapperFiles(project.toPath())
        second.mover = refusingTheJar()
        WrapperFiles.Result again = second.update()

        then:
        again.settled
        again.replaced.isEmpty()
        again.pending.keySet() == ['grails-wrapper.jar'] as Set
        again.done().contains(': grails-wrapper.jar is in use by the wrapper that runs this command and is replaced by the wrapper itself when it is next run. ')

        when: 'the wrapper is run: its script does what its line says'
        asTheScriptsDo()
        WrapperFiles.Result then = new WrapperFiles(project.toPath()).update()

        then:
        then.complete
        then.done().endsWith('nothing was changed')
        project.list().sort() == ['grails-app', 'grails-wrapper.jar', 'grailsw', 'grailsw.bat', 'settings.gradle']
    }

    void "a jar that cannot be replaced is left to the user where the batch file that is there does not move it"() {
        given: 'a batch file that cannot be replaced either'
        new File(project, 'grailsw.bat').setWritable(false)
        WrapperFiles files = new WrapperFiles(project.toPath())
        files.mover = refusingTheJar()

        when:
        WrapperFiles.Result result = files.update()

        then:
        !result.settled
        result.pending.isEmpty()
        result.beside.keySet() == ['grails-wrapper.jar'] as Set
        result.failed.keySet() == ['grailsw.bat'] as Set
        result.left()[0].startsWith('grails-wrapper.jar was not replaced: it could not be replaced (FileSystemException: ')
        result.left()[0].contains("The one of Grails ${VersionInfo.grailsVersion} is written beside it as grails-wrapper.jar.new. Once this command has ended, move it over grails-wrapper.jar in ${project.path} (")
        result.left()[0].endsWith((File.separatorChar == ('\\' as char) ? '(cmd /c move /y ' : '(mv -f ') + 'grails-wrapper.jar.new grails-wrapper.jar) and commit it')

        cleanup:
        new File(project, 'grailsw.bat').setWritable(true)
    }

    @Unroll
    void "the batch file of Grails 7 and 8 that the command interpreter is reading is replaced by one that ends that run and completes the update on the next (line ends: #name)"() {
        given: 'the batch file of an earlier wrapper, and Java started by it: the command interpreter goes on reading after that line'
        String earlier = earlierBatchFile().replace('\r\n', lineEnd)
        new File(project, 'grailsw.bat').setText(earlier, 'ISO-8859-1')
        int place = earlier.indexOf('grails.init.Start %*' + lineEnd) + ('grails.init.Start %*' + lineEnd).length()
        byte[] jarBefore = new File(project, 'grails-wrapper.jar').bytes
        WrapperFiles files = new WrapperFiles(project.toPath())
        files.batchFileRuns = true
        files.mover = refusingTheJar()

        when:
        WrapperFiles.Result result = files.update()
        String between = new File(project, 'grailsw.bat').getText('ISO-8859-1')

        then: 'both new files are beside the ones in use, and nothing is left to the user'
        result.settled && !result.complete
        result.replaced == ['grailsw']
        result.pending.keySet() == ['grailsw.bat', 'grails-wrapper.jar'] as Set
        result.left().isEmpty()
        new File(project, 'grailsw.bat.new').bytes == generated('grailsw.bat')
        new File(project, 'grails-wrapper.jar.new').bytes == generated('grails-wrapper.jar')
        new File(project, 'grails-wrapper.jar').bytes == jarBefore
        project.list().sort() == ['grails-app', 'grails-wrapper.jar', 'grails-wrapper.jar.new', 'grailsw', 'grailsw.bat', 'grailsw.bat.new', 'settings.gradle']

        and: 'where the command interpreter goes on reading, the batch file ends with the exit code of Java, on a line of its own'
        between.substring(place) == '@"%COMSPEC%" /c exit %ERRORLEVEL%\r\n'
        between.substring(0, place).endsWith('\r\n')

        and: 'from its first line it is a wrapper: it moves both files and starts the batch file that is then there, with what it was given'
        List<String> lines = between.readLines()
        lines[0].startsWith("@rem The Grails wrapper of this project is being replaced by that of Grails ${VersionInfo.grailsVersion}: ")
        lines[2] == '@if exist "%~dp0grails-wrapper.jar.new" move /y "%~dp0grails-wrapper.jar.new" "%~dp0grails-wrapper.jar" >NUL 2>&1'
        lines[3] == '@if exist "%~dp0grailsw.bat.new" move /y "%~dp0grailsw.bat.new" "%~dp0grailsw.bat" >NUL 2>&1 && "%~dp0grailsw.bat" %*'
        lines[6] == '@exit /b 1'
        lines.subList(7, lines.size() - 1).every { String line -> line.isBlank() && line.length() < 8191 }
        lines.every { String line -> line.startsWith('@') || line.isBlank() }
        between.every { String character -> (character as char) < (127 as char) }

        and:
        result.done() == "The Grails wrapper of the project in ${project.path} was that of Grails 8.1.0 and is being replaced by that of Grails ${VersionInfo.grailsVersion}: " +
                'grailsw was replaced, and grailsw.bat and grails-wrapper.jar are in use by the wrapper that runs this command and are replaced by the wrapper itself when it is next run. ' +
                'Run the wrapper once more, and then commit grailsw, grailsw.bat, grails-wrapper.jar; the files named .new are gone by then.'

        when: 'the wrapper is next run'
        asTheScriptsDo()

        then: 'the wrapper is that of this version in all its files'
        new WrapperFiles(project.toPath()).update().complete
        project.list().sort() == ['grails-app', 'grails-wrapper.jar', 'grailsw', 'grailsw.bat', 'settings.gradle']

        where:
        name                         | lineEnd
        'as the release has them'    | '\r\n'
        'as a checkout can have them' | '\n'
    }

    void "a run that is interrupted between the two files leaves the batch file it found, which is a wrapper"() {
        given:
        String earlier = earlierBatchFile()
        new File(project, 'grailsw.bat').setText(earlier, 'ISO-8859-1')
        WrapperFiles files = new WrapperFiles(project.toPath())
        files.batchFileRuns = true
        files.mover = { Path from, Path to ->
            if (to.fileName.toString() == 'grailsw.bat') {
                throw new FileSystemException(to.toString(), null, 'Access is denied')
            }
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        } as WrapperFiles.Mover

        when:
        WrapperFiles.Result result = files.update()

        then: 'the new one is beside it, and the user is told the one step, as it is typed in every shell of the system'
        new File(project, 'grailsw.bat').getText('ISO-8859-1') == earlier
        new File(project, 'grailsw.bat.new').bytes == generated('grailsw.bat')
        !result.settled
        result.beside.keySet() == ['grailsw.bat'] as Set
        result.left()[0].startsWith('grailsw.bat was not replaced: the command interpreter that started this command is reading it. ')
        result.left()[0].endsWith((File.separatorChar == ('\\' as char) ? '(cmd /c move /y ' : '(mv -f ') + 'grailsw.bat.new grailsw.bat) and commit it')
        !project.list().any { String name -> name.endsWith('.tmp') }
    }

    void "a batch file of this version is replaced while the command interpreter reads it, since it ends by a label"() {
        given: 'the batch file of this version as another release has it'
        new File(project, 'grailsw.bat').bytes = text('grailsw.bat').replace('grailsw startup script', 'grailsw start script').getBytes(StandardCharsets.ISO_8859_1)
        new File(project, 'grailsw.bat.new').text = 'what an earlier run left'
        WrapperFiles files = new WrapperFiles(project.toPath())
        files.batchFileRuns = true

        when:
        WrapperFiles.Result result = files.update()

        then:
        result.complete
        result.replaced == ['grailsw', 'grailsw.bat', 'grails-wrapper.jar']
        new File(project, 'grailsw.bat').bytes == generated('grailsw.bat')

        and: 'what was beside it is gone: the scripts would move it over the new one'
        !new File(project, 'grailsw.bat.new').exists()
    }

    void "a file that is written beside one that needs no replacing is removed"() {
        given:
        WrapperFiles.NAMES.each { String name -> new File(project, name).bytes = generated(name) }
        new File(project, 'grails-wrapper.jar.new').bytes = [1, 2, 3] as byte[]

        when:
        WrapperFiles.Result result = new WrapperFiles(project.toPath()).update()

        then:
        result.complete
        result.replaced.isEmpty()
        !new File(project, 'grails-wrapper.jar.new').exists()
    }

    @Unroll
    void "no batch file stands in for one whose line that starts Java is not known to be where the reading goes on: #title"() {
        given:
        new File(project, 'grailsw.bat').setText(batch, 'ISO-8859-1')

        expect:
        WrapperFiles.betweenRuns(new File(project, 'grailsw.bat').toPath(), '9.0.0') == null

        when: 'the new one is then written beside it, and the batch file is as it was'
        WrapperFiles files = new WrapperFiles(project.toPath())
        files.batchFileRuns = true
        WrapperFiles.Result result = files.update()

        then:
        result.beside.keySet() == ['grailsw.bat'] as Set
        new File(project, 'grailsw.bat').getText('ISO-8859-1') == batch
        new File(project, 'grailsw.bat.new').bytes == generated('grailsw.bat')

        where:
        title                                      | batch
        'it starts no Java'                        | '@rem the script of an earlier version\r\n'
        'it starts Java twice'                     | earlierBatchFile() + '"%JAVA_EXE%" -classpath "%CLASSPATH%" grails.init.Start %*\r\n'
        'it starts Java in a block'                | earlierBatchFile().replace('"%JAVA_EXE%" %DEFAULT', 'if defined JAVA_EXE (\r\n"%JAVA_EXE%" %DEFAULT').replace(':end\r\n', ')\r\n:end\r\n')
        'it starts Java and something after it'    | earlierBatchFile().replace('grails.init.Start %*', 'grails.init.Start %* & echo done')
        'the line is too near the beginning'       | '@echo off\r\n"%JAVA_HOME%\\bin\\java.exe" -classpath "%~dp0grails-wrapper.jar" grails.init.Start %*\r\n'
        'the line is a remark'                     | '@echo off\r\n@rem "%JAVA_EXE%" -classpath "%CLASSPATH%" grails.init.Start %*\r\n'
    }

    @Unroll
    void "the lines that fill the batch file up to the place are whole lines, however far it is: #place"() {
        given:
        Path batch = new File(project, 'grailsw.bat').toPath()
        String start = '"%JAVA_EXE%" grails.init.Start %*\r\n'
        // one remark of the length that puts the end of the line that starts Java at the place
        String before = '@rem ' + ('x' * (place - start.length() - 7)) + '\r\n'
        Files.write(batch, (before + start + ':end\r\n').getBytes(StandardCharsets.ISO_8859_1))

        when:
        byte[] between = WrapperFiles.betweenRuns(batch, '9.0.0')
        String text = between == null ? null : new String(between, StandardCharsets.ISO_8859_1)

        then:
        (text == null) == none
        none || text.substring(place) == '@"%COMSPEC%" /c exit %ERRORLEVEL%\r\n'
        none || text.substring(0, place).endsWith('\r\n')
        none || !text.replace('\r\n', '').contains('\r') && !text.replace('\r\n', '').contains('\n')
        none || text.readLines().every { String line -> line.length() <= 4000 }

        where:
        place << [600, headLength(), headLength() + 1, headLength() + 2, headLength() + 3, headLength() + 4000, headLength() + 4001, headLength() + 4002, 9000, 19000]
        none = place < headLength() || place == headLength() + 1
    }

    // the length of what stands before the lines that fill, for the version the features name
    static int headLength() {
        File file = File.createTempFile('grailsw', '.bat')
        try {
            file.setText('"%JAVA_EXE%" x\r\n' * 400 + '"%JAVA_EXE%" grails.init.Start %*\r\n', 'ISO-8859-1')
            String text = new String(WrapperFiles.betweenRuns(file.toPath(), '9.0.0'), StandardCharsets.ISO_8859_1)
            return text.indexOf('@exit /b 1\r\n') + '@exit /b 1\r\n'.length()
        } finally {
            file.delete()
        }
    }

    // what the batch file that stands in between two runs does, and for the jar the scripts of this version
    private void asTheScriptsDo() {
        ['grails-wrapper.jar', 'grailsw.bat'].each { String name ->
            File beside = new File(project, name + '.new')
            if (beside.exists()) {
                Files.move(beside.toPath(), new File(project, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private static WrapperFiles.Mover refusingTheJar() {
        return { Path from, Path to ->
            if (to.fileName.toString() == 'grails-wrapper.jar') {
                throw new FileSystemException(to.toString(), null, JAR_IN_USE)
            }
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        } as WrapperFiles.Mover
    }

    private static byte[] generated(String name) {
        return WrapperInUseSpec.classLoader.getResourceAsStream("grails-wrapper/${name}").bytes
    }

    private static String text(String name) {
        return new String(generated(name), StandardCharsets.ISO_8859_1)
    }

    /**
     * The batch file of the wrapper of Grails 7 and 8, as the start script template of Gradle 8 made it:
     * the line that starts Java is a command of its own, and what follows it is read from the file.
     */
    static String earlierBatchFile() {
        return ['@rem',
                '@rem Copyright 2015 the original author or authors.',
                '@rem',
                '@rem Licensed under the Apache License, Version 2.0 (the "License");',
                '@rem you may not use this file except in compliance with the License.',
                '@rem You may obtain a copy of the License at',
                '@rem',
                '@rem      https://www.apache.org/licenses/LICENSE-2.0',
                '@rem',
                '@rem Unless required by applicable law or agreed to in writing, software',
                '@rem distributed under the License is distributed on an "AS IS" BASIS,',
                '@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.',
                '@rem See the License for the specific language governing permissions and',
                '@rem limitations under the License.',
                '@rem',
                '@rem SPDX-License-Identifier: Apache-2.0',
                '@rem',
                '',
                '@if "%DEBUG%"=="" @echo off',
                '@rem ##########################################################################',
                '@rem',
                '@rem  grailsw startup script for Windows',
                '@rem',
                '@rem ##########################################################################',
                '',
                '@rem Set local scope for the variables with windows NT shell',
                'if "%OS%"=="Windows_NT" setlocal',
                '',
                'set DIRNAME=%~dp0',
                'if "%DIRNAME%"=="" set DIRNAME=.',
                '@rem This is normally unused',
                'set APP_BASE_NAME=%~n0',
                'set APP_HOME=%DIRNAME%',
                '',
                '@rem Resolve any "." and ".." in APP_HOME to make it shorter.',
                'for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi',
                '',
                '@rem Add default JVM options here. You can also use JAVA_OPTS and GRAILSW_OPTS to pass JVM options to this script.',
                'set DEFAULT_JVM_OPTS=',
                '',
                '@rem Find java.exe',
                'if defined JAVA_HOME goto findJavaFromJavaHome',
                '',
                'set JAVA_EXE=java.exe',
                '%JAVA_EXE% -version >NUL 2>&1',
                'if %ERRORLEVEL% equ 0 goto execute',
                '',
                'echo. 1>&2',
                'echo ERROR: JAVA_HOME is not set and no \'java\' command could be found in your PATH. 1>&2',
                '',
                'goto fail',
                '',
                ':findJavaFromJavaHome',
                'set JAVA_HOME=%JAVA_HOME:"=%',
                'set JAVA_EXE=%JAVA_HOME%/bin/java.exe',
                '',
                'if exist "%JAVA_EXE%" goto execute',
                '',
                'echo. 1>&2',
                'echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2',
                '',
                'goto fail',
                '',
                ':execute',
                '@rem Setup the command line',
                '',
                'set CLASSPATH=%APP_HOME%\\grails-wrapper.jar',
                '',
                '',
                '@rem Execute grailsw',
                '"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILSW_OPTS%  -classpath "%CLASSPATH%" grails.init.Start %*',
                '',
                ':end',
                '@rem End local scope for the variables with windows NT shell',
                'if %ERRORLEVEL% equ 0 goto mainEnd',
                '',
                ':fail',
                'rem Set variable GRAILSW_EXIT_CONSOLE if you need the _script_ return code instead of',
                'rem the _cmd.exe /c_ return code!',
                'set EXIT_CODE=%ERRORLEVEL%',
                'if %EXIT_CODE% equ 0 set EXIT_CODE=1',
                'if not ""=="%GRAILSW_EXIT_CONSOLE%" exit %EXIT_CODE%',
                'exit /b %EXIT_CODE%',
                '',
                ':mainEnd',
                'if "%OS%"=="Windows_NT" endlocal',
                '',
                ':omega',
                ''].join('\r\n')
    }
}
