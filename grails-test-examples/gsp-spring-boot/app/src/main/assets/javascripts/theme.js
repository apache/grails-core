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

// Light / dark / auto color-mode switcher for Bootstrap 5.3's `data-bs-theme`.
// The preference is persisted in localStorage; "auto" follows the operating system.
// This file is loaded in the document <head> so the theme is applied before the
// page paints, avoiding a flash of the wrong theme.
(() => {
    'use strict'

    const STORAGE_KEY = 'theme'
    const prefersDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches
    const storedTheme = () => localStorage.getItem(STORAGE_KEY)
    const preferredTheme = () => storedTheme() || 'auto'
    const icons = { light: 'bi-sun-fill', dark: 'bi-moon-stars-fill', auto: 'bi-circle-half' }

    const applyTheme = theme => {
        const resolved = theme === 'auto' ? (prefersDark() ? 'dark' : 'light') : theme
        document.documentElement.setAttribute('data-bs-theme', resolved)
    }

    // Apply immediately (before DOMContentLoaded) to avoid a flash of the wrong theme.
    applyTheme(preferredTheme())

    const showActiveTheme = theme => {
        document.querySelectorAll('[data-bs-theme-value]').forEach(button => {
            const active = button.getAttribute('data-bs-theme-value') === theme
            button.classList.toggle('active', active)
            button.setAttribute('aria-pressed', String(active))
            const check = button.querySelector('.bi-check')
            if (check) {
                check.classList.toggle('d-none', !active)
            }
        })
        document.querySelectorAll('.theme-icon-active').forEach(icon => {
            Object.values(icons).forEach(cls => icon.classList.remove(cls))
            icon.classList.add(icons[theme] || icons.auto)
        })
    }

    // Re-resolve "auto" when the OS preference changes.
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (preferredTheme() === 'auto') {
            applyTheme('auto')
        }
    })

    window.addEventListener('DOMContentLoaded', () => {
        showActiveTheme(preferredTheme())
        document.querySelectorAll('[data-bs-theme-value]').forEach(button => {
            button.addEventListener('click', () => {
                const theme = button.getAttribute('data-bs-theme-value')
                localStorage.setItem(STORAGE_KEY, theme)
                applyTheme(theme)
                showActiveTheme(theme)
            })
        })
    })
})()
