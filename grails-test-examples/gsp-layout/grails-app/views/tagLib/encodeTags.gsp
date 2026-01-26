<!DOCTYPE html>
<html>
<head>
    <title>Encode Tags Test</title>
</head>
<body>
    <h1>Encode Tags Test</h1>
    <p id="html-encoded">HTML Encoded: <g:encodeAs codec="HTML">${htmlContent}</g:encodeAs></p>
    <p id="raw-html" data-content="${htmlContent.encodeAsHTML()}">Raw attribute test</p>
    <p id="url-encoded">URL Encoded: ${urlContent.encodeAsURL()}</p>
</body>
</html>
