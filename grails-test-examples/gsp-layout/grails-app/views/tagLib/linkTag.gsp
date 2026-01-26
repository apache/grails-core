<!DOCTYPE html>
<html>
<head>
    <title>Link Tag Test</title>
</head>
<body>
    <h1>Link Tag Test</h1>
    <span id="index-link"><g:link controller="tagLib" action="index">Home Link</g:link></span>
    <g:link controller="tagLib" action="eachTag" class="styled-link" elementId="each-link">Each Tag Link</g:link>
    <g:link controller="tagLib" action="ifTag" params="[show: 'true']" elementId="param-link">With Params</g:link>
</body>
</html>
