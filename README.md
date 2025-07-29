# Grails Mail Plugin

[![Maven Central](https://img.shields.io/maven-central/v/org.grails.plugins/grails-mail.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.grails.plugins/grails-mail)
[![Java CI](https://github.com/grails-plugins/grails-mail/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/grails-plugins/grails-mail/actions/workflows/gradle.yml)

The Grails Mail Plugin provides a convenient Domain-Specific Language (DSL) for _sending_ email, supporting features such as plain text, HTML, attachments, inline resources and i18n (internationalization), among others.

Email can be sent using the `mailService.sendMail` method. Here's an example:
```groovy
mailService.sendMail {
   to 'fred@gmail.com', 'ginger@gmail.com'
   from 'john@gmail.com'
   cc 'marge@gmail.com', 'ed@gmail.com'
   bcc 'joe@gmail.com'
   subject 'Hello John'
   text 'this is some text'
}
```

## Documentation

- [Latest Release](https://grails-plugins.github.io/grails-mail/latest/)
- [Development Snapshot](https://grails-plugins.github.io/grails-mail/snapshot/)

## Versions

| Branch | Grails Version |
|--------|----------------|
| 1.x    | 2              |
| 2.x    | 3              |
| 3.x    | 4-5            |
| 4.x    | 6              |
| 5.x    | 7.0.0-M5+      |

## Issues

Issues can be raised via [GitHub Issues](https://github.com/grails-plugins/grails-mail/issues).

## Contributing

Pull requests are the preferred way to submit contributions. Before submitting, please create an issue using the [issue tracker](https://github.com/grails-plugins/grails-mail/issues), outlining the problem your contribution addresses.

For documentation contributions, creating an issue is not required.
