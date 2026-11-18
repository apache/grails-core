<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

# The Grails Upgrade Skill

`skills/grails-upgrade/SKILL.md` is the one agent skill for upgrading a Grails application. It is published with every Grails release as `org.apache.grails.skills:grails-upgrade`, and `.agents/skills/grails-upgrade` links to it for agents working in this repository.

There is one upgrade skill, not one per major version. The same file exists on every release branch and holds what an application needs to reach a release of that branch:

- the steps for upgrading from the previous major version, and
- the changes of every release of the current major version, one section per release.

## Keeping It Current

A pull request that changes what an application has to do, or what a call returns, updates the skill in the same pull request as the upgrade notes of the guide:

- Add the change under the heading of the release that will carry it, in the part for changes within the major version. Create the heading if the release has none yet. The release is the `projectVersion` of the branch the pull request targets, without `-SNAPSHOT`.
- Say what changed, what to search an application for, and how to keep the previous result when there is a way. Name a configuration setting and its default when the change adds one.
- Describe only what an application can see: public APIs, configuration, and build files.

The entry is merged up with the change. On a newer branch the same entry then sits under the first release of that branch that carries the change, so move it there when resolving the merge.

## At a Major Release

When the first release branch of a new major version is created, the skill is cleared on it:

- the part for upgrading from the previous major version is replaced with the steps for the new major version, and
- the release sections of the old major version are removed.

An application that skips a major version uses the skill of the last release of each major version in turn.
