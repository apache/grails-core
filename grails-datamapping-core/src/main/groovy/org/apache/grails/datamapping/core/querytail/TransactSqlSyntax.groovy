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
package org.apache.grails.datamapping.core.querytail

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Microsoft SQL Server and Sybase ASE: square brackets delimit a name, in which the closing bracket
 * is written twice; block comments nest; a dollar before digits is an amount; {@code #} and
 * {@code @} are part of a name; and double quotes delimit a string when {@code QUOTED_IDENTIFIER}
 * is off, as it is by default on Sybase, so they are always read as one.
 *
 * <p>A procedure takes a bare word as a string, as in {@code EXEC sp_password NULL, NewSecret, app},
 * so a statement that runs one takes {@linkplain #takesBareValues bare values}: one with {@code EXEC}
 * or {@code EXECUTE} anywhere in it, one with the JDBC {@code call} escape a driver turns into
 * {@code EXEC}, and one that opens with a word that is not a keyword, or with a bracketed name, which
 * runs the procedure it names when it is the first statement of the batch.</p>
 */
@CompileStatic
@PackageScope
final class TransactSqlSyntax extends StandardSqlSyntax {

    /** {@code EXEC} or {@code EXECUTE} as a word of its own, or the JDBC escape that calls a procedure. */
    private static final Pattern RUNS_PROCEDURE = Pattern.compile(
            '(?i)(?<![\\p{L}\\p{N}_@#$])EXEC(?:UTE)?(?![\\p{L}\\p{N}_@#$])|\\{\\s*(?:\\?\\s*=\\s*)?CALL\\b')

    /** The word or bracket a statement opens with, after any semicolons. */
    private static final Pattern OPENING = Pattern.compile('^[\\s;]*(\\[|[\\p{L}\\p{N}_@#$]+)')

    /**
     * The keywords a statement of SQL Server or Sybase opens with. Any other word a statement opens with
     * names the procedure it runs.
     */
    private static final Set<String> STATEMENT_KEYWORDS = ['ADD', 'ALTER', 'BACKUP', 'BEGIN', 'BREAK', 'BULK',
            'CHECKPOINT', 'CLOSE', 'COMMIT', 'CONNECT', 'CONTINUE', 'CREATE', 'DBCC', 'DEALLOCATE', 'DECLARE',
            'DELETE', 'DENY', 'DISABLE', 'DISCONNECT', 'DISK', 'DROP', 'DUMP', 'ENABLE', 'END', 'FETCH', 'GET',
            'GOTO', 'GRANT', 'IF', 'INSERT', 'KILL', 'LOAD', 'LOCK', 'MERGE', 'MOUNT', 'MOVE', 'ONLINE', 'OPEN',
            'PREPARE', 'PRINT', 'QUIESCE', 'RAISERROR', 'READTEXT', 'RECEIVE', 'RECONFIGURE', 'REORG', 'RESTORE',
            'RETURN', 'REVERT', 'REVOKE', 'ROLLBACK', 'SAVE', 'SELECT', 'SEND', 'SET', 'SETUSER', 'SHUTDOWN',
            'THROW', 'TRUNCATE', 'UNMOUNT', 'UPDATE', 'UPDATETEXT', 'USE', 'WAITFOR', 'WHILE', 'WITH',
            'WRITETEXT'] as Set<String>

    TransactSqlSyntax() {
        super('Transact-SQL')
    }

    @Override
    boolean takesBareValues(String redacted) {
        if (RUNS_PROCEDURE.matcher(redacted).find()) {
            return true
        }
        Matcher opening = OPENING.matcher(redacted)
        opening.find() && !STATEMENT_KEYWORDS.contains(opening.group(1).toUpperCase(Locale.ROOT))
    }

    @Override
    int endOfValue(String sql, int index) {
        if (sql.charAt(index) == '$' && standalone(sql, index) && startsNumber(sql, index + 1)) {
            return endOfNumber(sql, index + 1)
        }
        super.endOfValue(sql, index)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        if (sql.charAt(index) == '[') {
            return endOfQuoted(sql, index, ']' as char, false, true)
        }
        super.endOfQuotedName(sql, index)
    }

    @Override
    protected boolean nestedComments() {
        true
    }

    @Override
    protected boolean doubleQuotedStrings() {
        true
    }

    @Override
    protected boolean isNamePart(char c) {
        super.isNamePart(c) || c == '#' || c == '@'
    }
}
