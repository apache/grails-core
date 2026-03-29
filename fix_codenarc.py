import os
import re
import sys

def fix_file(file_path):
    with open(file_path, 'r') as f:
        content = f.read()

    original_content = content

    # 1. ClassStartsWithBlankLine
    # Look for class declaration followed by opening brace and then a non-blank line
    # (?![\s\n]*}) to avoid empty classes
    content = re.sub(r'(class\s+[^{]+\{\n)([ \t]*[^ \s\n/}])', r'\1\n\2', content)

    # 2. SpaceAroundMapEntryColon
    # Look for [key:value] or ,key:value or [key:value,
    # keys can be words, or quoted strings.
    # We use a more inclusive match for the key: either a word, or anything inside quotes.
    # Handle the colon and ensure there's no space after it.
    content = re.sub(r'([\[,]\s*(?:[\w\-.]+|\'[^\']+\'|"[^"]+")):([^\s/])', r'\1: \2', content)
    # Also for method calls if they start with a word or quoted string
    content = re.sub(r'(\(\s*(?:[\w\-.]+|\'[^\']+\'|"[^"]+")):([^\s/])', r'\1: \2', content)

    # 3. UnnecessaryGString
    # Replace "string" with 'string' if no $ and no ' inside.
    # We'll only do this for simple strings to avoid breaking things.
    def replace_gstring(match):
        s = match.group(0)
        inner = s[1:-1]
        if '$' not in inner and "'" not in inner and '\\' not in inner:
            return f"'{inner}'"
        return s

    # Match double quoted strings that don't span multiple lines
    content = re.sub(r'"[^" \n]*"', replace_gstring, content)
    # Match double quoted strings with spaces but no interpolation
    content = re.sub(r'"[^"$\n]*"', replace_gstring, content)

    # 4. UnnecessarySemicolon
    # Remove trailing semicolons (not in strings)
    content = re.sub(r'(?m);[ \t]*$', '', content)

    # 5. SpaceBeforeOpeningBrace
    # Ensure space before { if it's preceded by ) or ] or }
    content = re.sub(r'([\)\]\}])\{', r'\1 {', content)

    # 6. ConsecutiveBlankLines
    # Replace 3 or more newlines with 2 newlines
    content = re.sub(r'\n{3,}', '\n\n', content)

    if content != original_content:
        with open(file_path, 'w') as f:
            f.write(content)
        return True
    return False

def main(path):
    count = 0
    if os.path.isfile(path):
        if path.endswith('.groovy'):
            if fix_file(path):
                print(f"Fixed {path}")
                count += 1
    else:
        for root, dirs, files in os.walk(path):
            for file in files:
                if file.endswith('.groovy'):
                    file_path = os.path.join(root, file)
                    if fix_file(file_path):
                        print(f"Fixed {file_path}")
                        count += 1
    print(f"Total files modified: {count}")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        main(sys.argv[1])
    else:
        print("Usage: python fix_codenarc.py <directory>")
