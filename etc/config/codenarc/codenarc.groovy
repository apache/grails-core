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
ruleset {

    description 'A Codenarc ruleset for the Grails codebase'

    BracesForClass
    ClassStartsWithBlankLine {
        ignoreInnerClasses = true
    }
    ClosureStatementOnOpeningLineOfMultipleLineClosure
    ConsecutiveBlankLines
    FileEndsWithoutNewline
    NoTabCharacter
    DuplicateImport
    ImportFromSamePackage
    Indentation
    MisorderedStaticImports {
        comesBefore = false // static imports should come last
    }
    MissingBlankLineAfterImports
    MissingBlankLineAfterPackage
    MissingBlankLineBeforeAnnotatedField
    NoWildcardImports
    SpaceAfterCatch
    SpaceAfterClosingBrace
    SpaceAfterComma
    SpaceAfterFor
    SpaceAfterIf
    SpaceAfterMethodCallName
    SpaceAfterMethodDeclarationName
    SpaceAfterNotOperator
    SpaceAfterOpeningBrace {
        ignoreEmptyBlock = true
    }
    SpaceAfterSemicolon
    SpaceAfterSwitch
    SpaceAfterWhile
    SpaceAroundClosureArrow
    SpaceAroundMapEntryColon {
        characterAfterColonRegex = ' '
    }
    SpaceAroundOperator {
        ignoreParameterDefaultValueAssignments = false
    }
    SpaceBeforeClosingBrace {
        ignoreEmptyBlock = true
    }
    SpaceBeforeOpeningBrace
    SpaceInsideParentheses
    UnnecessaryConstructor
    UnnecessaryGroovyImport
    UnnecessaryGString
    UnnecessaryPublicModifier
    UnnecessarySemicolon
    UnusedImport
}
