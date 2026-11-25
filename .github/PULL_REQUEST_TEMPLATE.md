## Description
<!-- Describe your change and the problem it solves. Link to the related issue(s) if they exist. -->

## Contributor Checklist

Please review the following checklist before submitting your pull request. Pull requests that do not meet these requirements may be closed without review.

### Issue and Scope

- [ ] This PR is linked to an existing issue that has been **acknowledged or approved** by the project team. If no approved issue exists, please give background on why this change is necessary.  Tickets are preferred for release change log history.
- [ ] This PR addresses the **complete scope** of the linked issue. Partial implementations or unfinished work should not be submitted for review.
- [ ] This PR contains a **single, focused change**. Unrelated changes should be submitted as separate pull requests.
- [ ] This PR targets the **correct branch** for the type of change (see [Choosing the Branch](../CONTRIBUTING.md#choosing-the-branch)):
    - **Maintenance branches**, whose minor version has been released and that still get patch releases (e.g., `7.0.x`, `7.2.x`, `8.0.x`): Bug fixes only, released as the next patch version. No new features or API changes. A bug fix targets the oldest release branch that has the bug and still gets releases, and is merged up.
    - **Next minor branch**, until its minor version is released (e.g., `8.1.x` before 8.1.0): New features are welcome, but breaking existing APIs must be avoided.
    - **Next major branch**, until its major version is released (e.g., `9.0.x` before 9.0.0): Reserved for major changes. Breaking API changes are permitted.
- [ ] If this PR **changes a result**, it follows [Fixes That Change a Result](../CONTRIBUTING.md#fixes-that-change-a-result). A result is what a call that works today returns, stores, renders, binds or maps to, and a fix that makes such a call throw changes one too.
    - The description says which results change, how to get the old ones, and whether the fix needs a setting, and why.
    - The release notes and the upgrade guide section of the release that carries it have a note, and the upgrade skill (`grails-skills/upgrade/skills/grails-upgrade/SKILL.md`) has the same entry.
    - A setting it adds has a row on the Compatibility Settings page, and an issue for each later step: turning it on and removing it.
    - On a maintenance branch or the next minor branch, it waits for **two approving reviews**.

### Code Quality

- [ ] I have **added or updated tests** that cover the changes introduced in this PR. All code contributions are expected to include appropriate test coverage.
- [ ] I have verified that all existing tests pass by running `./gradlew build --rerun-tasks`.
- [ ] My code follows the project's **code style** guidelines. I have run `./gradlew codeStyle` and resolved any violations. See [Code Style](../CONTRIBUTING.md#code-style) for details.
- [ ] This PR does **not** include mass reformatting, style-only changes, or large-scale refactoring unless it was **explicitly approved** in the linked issue. Unsolicited reformatting will not be accepted.
- [ ] If generative AI tooling was used in preparing this contribution, a quality model was used to ensure contributions are **consistent with the project's quality standards**.

### Licensing and Attribution

- [ ] All contributed code is provided under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), and new source files include the appropriate **Apache license header**.
- [ ] I have the necessary rights to submit this contribution and confirm it is my own original work (see [Legal Notice](../CONTRIBUTING.md#i-want-to-contribute)).
- [ ] If generative AI tooling was used in preparing this contribution, I have followed the [Apache Software Foundation's policy on generative tooling](https://www.apache.org/legal/generative-tooling.html) and have properly attributed its use.

### Documentation

- [ ] If this PR introduces user-facing changes, I have included or updated the relevant documentation.
- [ ] If this PR adds a new feature, I have updated the **What's New** section of the Grails Guide.
- [ ] If this PR introduces breaking changes or changes that require user action during an upgrade, I have updated the **Upgrade Notes** for the corresponding version in the Grails Guide.
- [ ] The PR description clearly explains **what** was changed and **why**.

---

> **First-time contributors:** Please read our [Contributing Guide](../CONTRIBUTING.md) before submitting.
> Pull requests that appear to be auto-generated, incomplete, or unrelated to an approved issue may be
> closed to help maintainers focus on reviewed and planned work. We appreciate your understanding.
