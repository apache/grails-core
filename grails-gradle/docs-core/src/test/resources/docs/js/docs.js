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

function nextElement(el) {
    el = el.nextSibling;
    while (el && el.nodeType != 1) {
        el = el.nextSibling;
    }
    return el;
}
function indexOf(arr, o) {
    for (var i = 0; i < arr.length; i++) {
        if (arr[i] == o) return i;
    }
    return -1;
}
function contains(arr, o) { return indexOf(arr, o) != -1 }
function getClasses(el) { return el.className.split(" "); }
function pushClass(el, cls) {
    var classes = getClasses(el);
    classes.push(cls);
    el.className = classes.join(" ");
    return el.className;
}
function removeClass(el, cls) {
    var classes = getClasses(el);
    classes.splice(indexOf(classes, "selected"), 1)
    el.className = classes.join(" ");
    return el.className;
}
function toggleRef(el) {
    if (contains(getClasses(el), "selected")) {
        removeClass(el, "selected");
    }
    else {
        pushClass(el, "selected");
    }
}

var show = true;
function localToggle() {
    document.getElementById("col2").style.display = show ? "none" : "";
    document.getElementById("toggle-col1").style.display = show ? "inline" : "none";
    document.getElementById("ref-button").parentNode.className = (show = !show) ? "separator selected" : "separator";
    return false;
}
function toggleNavSummary(hide) {
    document.getElementById("nav-summary-childs").style.display = !hide ? "block" : "none";
    document.getElementById("nav-summary").className = hide ? "" : "active";
}

var hiddenBlocksShown = false;
function toggleHidden() {
    var elements = document.getElementsByClassName("hidden-block");
    for (var i = 0; i < elements.length; i++) {
        elements[i].style.display = hiddenBlocksShown ? "none" : "block";
    }

    hiddenBlocksShown = !hiddenBlocksShown
}
