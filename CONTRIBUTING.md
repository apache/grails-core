<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<!-- omit in toc -->
# Contributing to Grails

First off, thanks for taking the time to contribute! ❤️

Grails is an open source project with an active community and we rely heavily on that community to help make Grails better. As such, there are various ways in which people can contribute to Grails. One of these is by [writing useful plugins](https://docs.grails.org/latest/guide/plugins.html) and making them publicly available.

All types of contributions are encouraged and valued. See the [Table of Contents](#table-of-contents) for different ways to help and details about how this project handles them. Please make sure to read the relevant section before making your contribution. It will make it a lot easier for us maintainers and smooth out the experience for all involved. The community looks forward to your contributions. 🎉

> And if you like the project, but just don't have time to contribute, that's fine. There are other easy ways to support the project and show your appreciation, which we would also be very happy about:
> - Star the project
> - Talk about it on Social Media
> - Refer to this project in your project's readme
> - Mention the project at local meetups and tell your friends/colleagues
> - As an independent initiative, community members have set up an open collective for Apache Grails - https://opencollective.com/friends-of-grails. This initiative is designed to complement the Apache project and the many contributions we get from our great community and supporters.

<!-- omit in toc -->
## Table of Contents

- [I Have a Question](#i-have-a-question)
- [I Want to Write a Grails Plugin](#i-want-to-write-a-grails-plugin)
- [I Want To Contribute](#i-want-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)
- [Your First Code Contribution](#your-first-code-contribution)
- [Improving The Documentation](#improving-the-documentation)
- [Code Style](#code-style)
- [Commit Messages](#commit-messages)
- [Change Review Process](#change-review-process)
- [Join The Project Team](#join-the-project-team)



## I Have a Question

> If you want to ask a question, we assume that you have read the available [Documentation](https://docs.grails.org/latest/).

Before you ask a question, it is best to search for existing [Issues](https://github.com/apache/grails-core/issues) that might help you. In case you have found a suitable issue and still need clarification, you can write your question in this issue. It is also advisable to search the internet for answers first.

If you then still feel the need to ask a question and need clarification, we recommend the following:

- Ask a question on [Stack Overflow](https://stackoverflow.com/questions/tagged/grails).
- Chat with us on [Slack](https://grails.slack.com).
- If your question is due to a bug, open an [Issue](https://github.com/apache/grails-core/issues/new).
  - Provide as much context as you can about what you're running into.
  - Provide project and platform versions (nodejs, npm, etc), depending on what seems relevant.

## I Want to Write a Grails Plugin

[Grails Plugins](https://docs.grails.org/latest/guide/plugins.html) are similar to a Grails Application project, but they include a plugin descriptor and package shared functionality for other Grails Applications to use.  More information can be found [here](https://docs.grails.org/latest/guide/plugins.html#creatingAndInstallingPlugins).

## I Want to Contribute

> ### Legal Notice <!-- omit in toc -->
> When contributing to this project, you must agree that you have authored 100% of the content, that you have the
> necessary rights to the content and that the content you contribute may be provided under the project license.

### Reporting Bugs

<!-- omit in toc -->
#### Before Submitting a Bug Report

A good bug report shouldn't leave others needing to chase you up for more information. Therefore, we ask you to investigate carefully, collect information and describe the issue in detail in your report. Please complete the following steps in advance to help us fix any potential bug as fast as possible.

- Make sure that you are using the latest version.
- Determine if your bug is really a bug and not an error on your side e.g. using incompatible environment components/versions (Make sure that you have read the [documentation](https://docs.grails.org/latest/). If you are looking for support, you might want to check [this section](#i-have-a-question)).
- To see if other users have experienced (and potentially already solved) the same issue you are having, check if there is not already a bug report existing for your bug or error in the [bug tracker](https://github.com/apache/grails-coreissues?q=label%3Abug).
- Also make sure to search the internet (including Stack Overflow) to see if users outside of the GitHub community have discussed the issue.
- Collect information about the bug:
- Stack trace (Traceback)
- OS, Platform and Version (Windows, Linux, macOS, x86, ARM)
- Version of the interpreter, compiler, SDK, runtime environment, package manager, depending on what seems relevant.
- Possibly your input and the output
- Can you reliably reproduce the issue? And can you also reproduce it with older versions?  Sample projects always are the best at investigating bugs.

<!-- omit in toc -->
#### How Do I Submit a Good Bug Report?

> You must never report security related issues, vulnerabilities or bugs including sensitive information to the issue tracker, or elsewhere in public. Instead, sensitive bugs must be sent by email to security@grails.org.

Grails uses GitHub to track issues in the [core framework](https://github.com/apache/grails-core/issues). Similarly, for its documentation there is a [separate tracker](https://github.com/apache/grails-doc/issues).  If you run into an issue with the project:

- Open an [Issue](https://github.com/apache/grails-core/issues/new).
- Explain the behavior you would expect and the actual behavior.
- Please provide as much context as possible and describe the *reproduction steps* that someone else can follow to recreate the issue on their own. This usually includes your code. For good bug reports you should isolate the problem and create a reduced test case.
- Provide the information you collected in the previous section.

Once it's filed:

- The project team will label the issue accordingly.
- A team member will try to reproduce the issue with your provided steps. If there are no reproduction steps or no obvious way to reproduce the issue, the team will ask you for those steps and mark the issue as `info: need review`. Bugs with the `info: need review` tag will not be addressed until they are reproduced.
- If the team is able to reproduce the issue, it will be marked `status: acknowledged`, as well as possibly other tags (such as `type: critical`), and the issue will be left to be [implemented by someone](#your-first-code-contribution).


### Suggesting Enhancements

This section guides you through submitting an enhancement suggestion for Grails, **including completely new features and minor improvements to existing functionality**. Following these guidelines will help maintainers and the community to understand your suggestion and find related suggestions.

<!-- omit in toc -->
#### Before Submitting an Enhancement

- Make sure that you are using the latest version.
- Read the [documentation](https://docs.grails.org/latest/) carefully and find out if the functionality is already covered, maybe by an individual configuration.
- Perform a [search](https://github.com/apache/grails-core/issues) to see if the enhancement has already been suggested. If it has, add a comment to the existing issue instead of opening a new one.
- Find out whether your idea fits with the scope and aims of the project. It's up to you to make a strong case to convince the project's developers of the merits of this feature. Keep in mind that we want features that will be useful to the majority of our users and not just a small subset. If you're just targeting a minority of users, consider writing an add-on/plugin library.

<!-- omit in toc -->
#### How Do I Submit a Good Enhancement Suggestion?

Enhancement suggestions are tracked as [GitHub issues](https://github.com/apache/grails-core/issues).

- Use a **clear and descriptive title** for the issue to identify the suggestion.
- Provide a **step-by-step description of the suggested enhancement** in as many details as possible.
- **Describe the current behavior** and **explain which behavior you expected to see instead** and why. At this point you can also tell which alternatives do not work for you.
- **Explain why this enhancement would be useful** to most Grails users. You may also want to point out the other projects that solved it better and which could serve as inspiration.

### Your First Code Contribution

<!-- omit in toc -->
#### Environment Setup

##### 1. Forking the Code
One of the benefits of [GitHub](https://github.com) is the way that you can easily contribute to a project by [forking the repository](https://help.github.com/articles/fork-a-repo/) and [sending pull requests](https://help.github.com/articles/creating-a-pull-request/) with your changes.  Please see GitHub's guides on how to create a fork and submit that fork.  For easier illustration, the remainder of this document will use the `grails` repositories, but when working locally you should use your own fork.

##### 2. Tool Setup

If you're interested in contributing fixes and features to any part of grails, you will have to learn how to get hold of the project's source, build it and test it with your own applications. Before you start, make sure you have:

* A git client
* An Editor such as [IntelliJ](https://www.jetbrains.com/idea/)
* A JDK (17 or higher)
* A container runtime

Once you have the pre-requisite packages installed, the next step is to download the Apache Grails source code, which is
hosted at [GitHub](https://github.com/apache/grails-core). This is a simple case of cloning the repository you're
interested in. For example, to get the core framework run:

    git clone http://github.com/apache/grails-core.git

This will create a `grails-core` directory in your current working directory containing all the project source files.
The next step is setting up the JDK to use. Grails makes use of [SDKMAN!](https://sdkman.io/) for easy JDK setup. Each
Grails project should have a `.sdkmanrc` in it's root directory. Change to the `grails-core` directory and install the
preferred JDK by issuing the command:

    sdk env .

##### 3. Building the Code

To build the code, without tests, simply run:

    ./gradlew build -PskipTests

If you encounter out of memory errors when trying to run the build, try adjusting Gradle build settings. For example:

    export GRADLE_OPTS="-Xms2G -Xmx5G"

Please note that a valid container runtime is required to run Grails Tests. The example above omits the tests so the
build will pass.

<!-- omit in toc -->
#### Testing Your Change

Grails has test coverage in the form of `unit`, `integration`, and `functional` tests.

To force a full run of the full suite of tests in `grails-core` execute:

    ./gradlew build --rerun-tasks

Check the test results & verify that the build completes successfully.

##### Advanced Troubleshooting
Sometimes it's useful to debug your local application to see what's going wrong.  Instead of using `./gradlew bootRun` use:

    ./gradlew bootRun --debug-jvm

By default, Grails forks a JVM to run the application. The `-debug-jvm` argument causes the debugger to be associated with the forked JVM.  You can then attach your debugger as proceed as normal.

### Improving The Documentation

There are many aspects to [Grail's documentation](https://docs.grails.org/)
- [API](https://docs.grails.org/latest/api/) documentation in the code itself via javadoc & groovydoc.
- [The Grails User Guide](https://docs.grails.org/latest/guide/single.html) from the `grails-doc` project in this
  repository.
- Various how-to [Guides](https://guides.grails.org/index.html) from
  the [grails-guides](https://github.com/grails-guides) Organization.

<!-- omit in toc -->
#### <u>Improving the User Guide</u>
The user guide is written using [Asciidoctor](https://asciidoctor.org/docs/user-manual/). The simplest way to contribute fixes is to simply click on the "Improve this doc" link that is to the right of each section of the documentation.

This will link to the GitHub edit screen where you can make changes, preview them and create a pull request.

<!-- omit in toc -->
#### <u>Building the Guide</u>

If you want to make significant changes, such as changing the structure of the table of contents etc. then we recommend
you build the user guide. The source files can be found in the `grails-doc/src/en/guide` directory. Whilst the Table of
Contents (TOC) is defined in the `grails-doc/src/en/guide/toc.yml` file.

Each YAML key points to a Asciidoc template. For example consider the following YAML:

    introduction:
    title: Introduction
    whatsNew:
    title: What's new in Grails 3.2?
    ...

The `introduction` key points to `grails-doc/src/en/guide/introduction.adoc`. The `title` key defines the title that is
dislayed in the TOC. Because `whatsNew` key is nested underneath the `introduction` key it points to
`grails-doc/src/en/guide/introduction/whatsNew.adoc`, which is nested in a directory called `introduction`.

Essentially, using the `toc.yml` file and the directory structure you can manipulate the structure of the user guide.

To generate the documentation run the `publishGuide` in the `grails-doc` task:

    ./gradlew :grails-doc:publishGuide -x aggregateGroovydoc

NOTE: In the above example we skip the `aggregateGroovydoc` task to speed up building of the guide, otherwise all
Groovydoc documentation will be built too!

Once the guide is built simply open the `grails-doc/build/original-guide/index.html` file in a browser to review your
changes.

## Code Style

Grails code style mostly mirrors the Spring Framework's [Style Guide](https://github.com/spring-projects/spring-framework/blob/main/CONTRIBUTING.md#source-code-style).  We are currently working on a more detailed proposal under ticket [#13754](https://github.com/apache/grails-core/issues/13754).

### Commit Messages

Grails makes use of [Release Drafter](https://github.com/release-drafter/release-drafter) to draft its release notes so
commit messages are important. They should follow the project's rules. While a change can be incrementally made under
many commits, pull requests should be squashed into a single, meaningful commit message.

### Change Review Process

The Apache Grails project uses different review processes based on the change being made. We use both a
`Review then Commit` policy and a `Commit then Review` policy. These policies only apply to what we consider `protected`
branches - where a `protected` branch is a branch that will result in published code or tracks an Apache Grails release
version (i.e. 7.0.x is for the Apache Grails 7 release). Development will often occur in side branches without review
and the review will be performed at the time of merging those changes into a `protected` branch.

Concerning when a review is required for a `protected` branch:
* Build Related Changes - `Commit then Review`
   * Due to the build changes being related to GitHub workflows and not being able to test locally.
* Documentation Changes - obvious fixes should be allowed without review, otherwise only minimum 1 reviewer with `Commit then Review`
* Groovy or Spring Dependency Changes - `Review then Commit` and mandated waiting period
   * Reasoning: due to the impact of past Spring & Groovy upgrades, it often breaks the entire development chain.  These changes should not be merged without significant review.
   * Minimum # of Reviewers:  2 reviewers, preferred 3
   * Mandated Waiting Period: 3 days if over a weekend, 1 day if during the week.
* Otherwise - `Review then Commit` with only 1 reviewer required. 

## Join The Project Team
For people willing to contribute more than an occasional pull request, consider joining our core team.  Inquire in the `questions` channel in slack to learn more.
