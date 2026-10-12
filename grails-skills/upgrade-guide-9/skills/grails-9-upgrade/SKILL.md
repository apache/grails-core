---
name: grails-9-upgrade
description: Guide for upgrading Grails applications from Grails 8.x to Grails 9, covering the Groovy 6 baseline, RxGORM on RxJava 3 (imports, blocking calls, subscribers, empty results), and the deprecated RxJava 1 and RxJava 2 async and events modules
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Guide upgrades of Grails applications from Grails 8.x to Grails 9.
- Turn the Grails 9 upgrade guide into a practical migration checklist.
- Identify the Groovy 6, RxGORM, and RxJava changes that can break an application.
- Keep migration work focused on public application behavior, not internal Grails framework implementation details.

## When to Use Me

Activate this skill when:

- Updating an application to Grails 9.
- Reviewing a Grails 9 upgrade branch or pull request.
- Fixing tests, build failures, runtime failures, or behavior changes after moving from Grails 8.x to Grails 9.
- Porting code that uses RxGORM, `grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava`, or `grails-events-rxjava2` to RxJava 3.

For an application still on Grails 7, apply the `grails-8-upgrade` skill first and move to Grails 8 before using this one.

## Primary Sources

Use the published Grails documentation as the source of truth before changing an application. The URLs below use `<version>`, which stands for the Grails 9 version the application is upgrading to, such as the `grailsVersion` in its `gradle.properties`; use `snapshot` for a snapshot version.

| URL | Use for |
|-----|---------|
| `https://grails.apache.org/docs/<version>/guide/upgrading.html#upgrading90x` | Main Grails 8 to Grails 9 upgrade guide |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#whatsNew` | Grails 9 feature and platform overview |
| `https://grails.apache.org/docs/<version>/guide/introduction.html#dependencyUpgrades` | Platform dependency baseline |
| `https://grails.apache.org/docs/<version>/guide/async.html` | Promises and events, including the RxJava 3 modules |
| `https://grails.apache.org/docs/<version>/ref/Versions/Grails%20BOM.html` | Dependency versions of the Grails BOM |
| `https://groovy-lang.org/releasenotes/groovy-6.0.html` | Groovy 6 language and runtime changes |
| `https://github.com/ReactiveX/RxJava/wiki/What's-different-in-3.0` | RxJava 3 API differences, with the RxJava 1 to 2 differences it links to |

## Upgrade Baseline

Verify these before touching application code; `gradle.properties`, the Gradle wrapper, and the Grails BOM are the sources of truth:

- Java 21 remains the minimum to build and run.
- Groovy moves from 5.x to 6.0, and Spock to the `groovy-6.0` variant. Do not pin `groovy-all` or Spock yourself; the Grails BOM manages both.
- Spring Boot 4.1 and Spring Framework 7 are unchanged from Grails 8; a Grails 8 application's Boot configuration carries over.
- Gradle 9.8 or later for the wrapper.
- RxGORM (`grails-datamapping-rx`) depends on `io.reactivex.rxjava3:rxjava`. The RxJava 1 artifact `io.reactivex:rxjava` is no longer transitively available through it.

## Migration Workflow

1. Update `grailsVersion` in `gradle.properties` and refresh the Gradle wrapper; let the Grails BOM manage Groovy, Spock, and RxJava versions.
2. Build without tests first (`./gradlew compileGroovy compileTestGroovy`) and fix compilation errors module by module.
3. Port RxGORM and RxJava code, following the sections below.
4. Replace the deprecated RxJava 1 and 2 async and events modules with their RxJava 3 counterparts.
5. Run the full test suite, then start the application and read its log for `[DEPRECATED]` and `deprecated` warnings from Grails.
6. Review the published upgrade guide section by section, confirming every item either does not apply or has been handled.

## Groovy 6

Groovy 6 is a major Groovy release. Read its release notes before assuming that a Groovy 5 construct behaves the same way. Compile the application statically where it was already static; a construct that compiles dynamically but fails under `@CompileStatic` is a sign of a Groovy change rather than a Grails one. Keep the Spock version the Grails BOM supplies, which is built for Groovy 6.

## RxGORM on RxJava 3

RxGORM returns RxJava 3 types. Every `rx.Observable` becomes `io.reactivex.rxjava3.core.Observable`, and an `@RxSchedule` service method that returned `rx.Single` returns `io.reactivex.rxjava3.core.Single`.

### Imports

| RxJava 1 | RxJava 3 |
|----------|----------|
| `rx.Observable` | `io.reactivex.rxjava3.core.Observable` |
| `rx.Single` | `io.reactivex.rxjava3.core.Single` |
| `rx.Subscriber` | `io.reactivex.rxjava3.core.Observer` |
| `rx.Subscription` | `io.reactivex.rxjava3.disposables.Disposable` |
| `rx.Scheduler`, `rx.schedulers.Schedulers` | `io.reactivex.rxjava3.core.Scheduler`, `io.reactivex.rxjava3.schedulers.Schedulers` |
| `rx.functions.Func1`, `Func2`, `FuncN` | `io.reactivex.rxjava3.functions.Function`, `BiFunction`, `Function<Object[], R>` |
| `rx.observers.TestSubscriber` | `io.reactivex.rxjava3.observers.TestObserver` |

A declared return type such as `rx.Observable<Book>` in a `@Service` interface must change too, or the service implementer no longer recognises the method as reactive.

### Blocking Calls

| RxJava 1 | RxJava 3 |
|----------|----------|
| `observable.toBlocking().first()` | `observable.blockingFirst()` |
| `observable.toBlocking().single()` | `observable.blockingSingle()` |
| `observable.toList().toBlocking().single()` | `observable.toList().blockingGet()` |
| `single.toBlocking().value()` | `single.blockingGet()` |
| `observable.toBlocking().first()` compared with `null` | `observable.firstElement().blockingGet()`, which returns `null` when empty, or expect `blockingFirst()` to throw `NoSuchElementException` |

`Observable.toList()` returns a `Single<List<T>>` in RxJava 3. The RxGORM methods that emit a list, such as `list()`, `DetachedCriteria.toList()`, criteria `list()` and `toListObservable()` on a lazy association, still return `Observable<List<T>>`, so subscribers to them do not change.

### Subscribers

- Closure subscriptions, `observable.subscribe { Book book -> ... }` and the two-closure `subscribe(onNext, onError)`, are unchanged.
- A full subscriber implements `Observer<T>`: add `onSubscribe(Disposable d)`, rename `onCompleted()` to `onComplete()`, and keep the `Disposable` to cancel instead of calling `unsubscribe()` on a `Subscription`.
- `DetachedCriteria`, `ObservableProxy` and the lazy association collections implement `ObservableSource<T>`. Their `subscribe(Observer)` returns `void`; code that kept the returned `Subscription` keeps the `Disposable` from `onSubscribe` instead. They can be passed to `Observable.wrap(..)`, `concatWith(..)` and any other operator that takes an `ObservableSource`.
- `TestSubscriber` tests become `TestObserver` tests: `awaitTerminalEvent(..)` is `await(..)`, `assertCompleted()` is `assertComplete()`, and `onNextEvents` is `values()`.

### Empty Results and Null

RxJava 3 never emits `null`.

- A query with no result completes empty, as before.
- `save()`, `insert()` and `persist()` on an instance the datastore did not write complete empty instead of emitting `null`.
- An `@RxSchedule` service method returning `Single` whose body returns `null` signals `java.util.NoSuchElementException`; one returning `Observable` completes empty. Return a `Maybe`-like outcome by declaring `Observable` and checking `isEmpty()`, or catch the exception where a missing result is expected.
- A `switchMap` or `flatMap` function must return an `ObservableSource`, never `null`; use `Observable.empty()`.

### Datastore Implementations

A datastore implementation of RxGORM compiled for Grails 8 or earlier, such as the RxGORM for MongoDB plugin, links against RxJava 1 and fails at runtime with `NoClassDefFoundError: rx/Observable`. It must be rebuilt against this release. The `rx-mongodb` profile feature adds `grails-async-rxjava3` to a generated application.

## Deprecated RxJava 1 and RxJava 2 Modules

`grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava` and `grails-events-rxjava2` are deprecated for removal. They still work and log a warning such as `The RxJava 1 promise factory (grails-async-rxjava) is deprecated` when the promise factory or event bus is created.

- Replace `org.apache.grails.async:grails-async-rxjava` or `grails-async-rxjava2` with `grails-async-rxjava3`.
- Replace `org.apache.grails.events:grails-events-rxjava` or `grails-events-rxjava2` with `grails-events-rxjava3`.
- The `Promise` and `EventBus` APIs are the same for every implementation, so an application that only uses the Grails APIs needs no code change. Code that reaches into the RxJava types behind them, such as `RxPromise.toObservable()`, moves to the RxJava 3 types as described above.
- Configure RxJava 3 scheduling through `io.reactivex.rxjava3.plugins.RxJavaPlugins` instead of RxJava 1 `RxJavaHooks` or RxJava 2 `RxJavaPlugins`.

## Verification Checklist

- [ ] `gradle.properties` names the Grails 9 version and the wrapper is Gradle 9.8 or later
- [ ] No source file imports `rx.` or `io.reactivex.` without the `rxjava3` segment
- [ ] No test calls `toBlocking()`
- [ ] No build file declares `io.reactivex:rxjava`, `io.reactivex.rxjava2:rxjava`, `grails-async-rxjava`, `grails-async-rxjava2`, `grails-events-rxjava` or `grails-events-rxjava2`
- [ ] Every `Observer` implementation has `onSubscribe(Disposable)` and `onComplete()`
- [ ] Every `switchMap` and `flatMap` function returns an `ObservableSource`, never `null`
- [ ] The application starts with no `deprecated` warning from the Grails async or events modules
- [ ] The full test suite passes on Groovy 6

## Pitfalls to Avoid

- Do not add `io.reactivex:rxjava` back to make old imports compile; port the code instead. Two RxJava generations on one classpath hide the mismatch until a datastore or plugin hands the application the wrong `Observable`.
- Do not compare a blocking result with `null` to detect an empty result; RxJava 3 throws instead.
- Do not keep a `Subscription` field for an RxGORM subscription; the handle is the `Disposable` passed to `onSubscribe`.
- Do not pin Groovy, Spock or RxJava versions in the application build; the Grails BOM manages them.
