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

import groovy.transform.CompileStatic

/**
 * A rule a command holds the options and parameters it is given to, beyond what each of them takes on
 * its own: which of them cannot be given together, and which needs another. The command checks the
 * rule itself, and refuses a command line that breaks it as a mistake in the command line; the rule is
 * also said here, so that {@code help --json} can describe it for a tool.
 *
 * <p>A member is an option by its longest name, or a parameter by its label.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class OptionRelation {

    /** At most one of the members is given; with {@link #required}, exactly one. */
    public static final String EXCLUSIVE = 'exclusive'

    /** When one of the members is given, every one of {@link #requires} is given too. */
    public static final String REQUIRES = 'requires'

    /** What kind of rule it is: {@link #EXCLUSIVE} or {@link #REQUIRES}. */
    final String kind

    /** The options and parameters the rule is about. */
    final List<String> members

    /** For {@link #EXCLUSIVE}: whether one of the members has to be given. */
    final boolean required

    /** For {@link #REQUIRES}: what has to be given with a member. */
    final List<String> requires

    private OptionRelation(String kind, List<String> members, boolean required, List<String> requires) {
        this.kind = kind
        this.members = members.asImmutable()
        this.required = required
        this.requires = requires.asImmutable()
    }

    /**
     * @param required whether one of them has to be given
     * @param members options and parameters of which at most one is given
     * @return the rule
     */
    static OptionRelation exclusive(boolean required, String... members) {
        return new OptionRelation(EXCLUSIVE, members.toList(), required, Collections.<String>emptyList())
    }

    /**
     * @param member an option or a parameter
     * @param requires what has to be given with it
     * @return the rule
     */
    static OptionRelation requires(String member, String... requires) {
        return new OptionRelation(REQUIRES, [member], false, requires.toList())
    }

    /**
     * @return the rule as {@code help --json} writes it
     */
    Map<String, Object> describe() {
        Map<String, Object> described = new LinkedHashMap<>()
        described.put('kind', kind)
        described.put('members', new ArrayList<String>(members))
        if (kind == EXCLUSIVE) {
            described.put('required', required)
        } else {
            described.put('requires', new ArrayList<String>(requires))
        }
        return described
    }

    /**
     * A command that has rules of this kind.
     */
    static interface Declared {

        /**
         * @return the rules the command holds what it is given to, each of which it checks itself
         */
        List<OptionRelation> getOptionRelations()
    }
}
