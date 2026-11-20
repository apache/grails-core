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

# app4

A dedicated Grails functional test application for wildcard URL mapping validation.

This app exists to verify the user-visible HTTP behavior of wildcard URL mappings without relying on internal framework APIs in the functional tests. It is intentionally small and focused on routing behavior that should remain stable across framework changes.

## Purpose

`app4` provides focused functional coverage for wildcard URL mapping behavior, including wildcard resolution, fallback routing, and precedence between wildcard, literal, and method-specific mappings.

It is intended as a small regression test app for framework changes in URL mapping behavior.
