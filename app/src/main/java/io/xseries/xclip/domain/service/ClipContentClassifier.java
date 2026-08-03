

/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.util.TextValues;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, local-only classifier for clipboard text.
 *
 * Classification precedence is intentionally strict:
 * URL -> PATH -> JSON -> COMMAND -> CODE -> TEXT.
 * Strong signatures are preferred over broad guesses to avoid noisy badges.
 */
public final class ClipContentClassifier {

    private static final int MAX_JSON_SCAN_CHARS = 1_000_000;

    private static final Pattern WINDOWS_DRIVE_PATH =
            Pattern.compile("^[A-Za-z]:[\\\\/][^\\r\\n]*$");
    private static final Pattern WINDOWS_UNC_PATH =
            Pattern.compile("^\\\\\\\\[^\\\\/\\s]+[\\\\/][^\\\\/\\s]+(?:[\\\\/][^\\r\\n]*)?$");
    private static final Pattern WINDOWS_ENV_PATH =
            Pattern.compile("^%[A-Za-z_][A-Za-z0-9_]*%[\\\\/][^\\r\\n]+$");
    private static final Pattern UNIX_ABSOLUTE_PATH =
            Pattern.compile("^/(?:[^/\\r\\n]+/)+[^\\r\\n]*$");
    private static final Pattern FILE_URI_PATH =
            Pattern.compile("^file:/+[^\\r\\n]+$", Pattern.CASE_INSENSITIVE);

    private static final Pattern POWERSHELL_PROMPT =
            Pattern.compile("^PS\\s+[^>\\r\\n]+>\\s*\\S.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CMD_PROMPT =
            Pattern.compile("^[A-Za-z]:\\\\[^>\\r\\n]*>\\s*\\S.*$");
    private static final Pattern POWERSHELL_CMDLET =
            Pattern.compile(
                    "^(?:Get|Set|New|Remove|Test|Invoke|Start|Stop|Enable|Disable|Update|Write|"
                            + "Select|Where|ForEach|Measure|Format|Out|Export|Import|Copy|Move|Rename)"
                            + "-[A-Za-z][A-Za-z0-9]*\\b.*$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s*```", Pattern.DOTALL);
    private static final Pattern HTML_OR_XML =
            Pattern.compile(
                    "^\\s*(?:<\\?xml\\b|<!DOCTYPE\\s+html\\b|<(?:html|head|body|div|span|script|style|"
                            + "section|main|article|button|input|form|table|svg)\\b)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
    private static final Pattern SQL =
            Pattern.compile(
                    "^\\s*(?:SELECT\\b.+\\bFROM\\b|INSERT\\s+INTO\\b|UPDATE\\b.+\\bSET\\b|"
                            + "DELETE\\s+FROM\\b|CREATE\\s+(?:TABLE|INDEX|VIEW)\\b|ALTER\\s+TABLE\\b|"
                            + "WITH\\b.+\\bSELECT\\b)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
    private static final Pattern DECLARATIVE_CODE =
            Pattern.compile(
                    "^\\s*(?:package\\s+[\\w.]+\\s*;|import\\s+[\\w.*]+\\s*;|"
                            + "(?:public|private|protected|internal|static|final|abstract|sealed)\\s+"
                            + "(?:class|interface|enum|record|void|[A-Za-z_$][\\w$<>?, .\\[\\]]*)\\b|"
                            + "class\\s+[A-Za-z_]\\w*\\s*(?:[:({]|$)|"
                            + "def\\s+[A-Za-z_]\\w*\\s*\\(|async\\s+def\\s+[A-Za-z_]\\w*\\s*\\(|"
                            + "function\\s+[A-Za-z_$]\\w*\\s*\\(|"
                            + "(?:const|let|var)\\s+[A-Za-z_$]\\w*\\s*=|"
                            + "#include\\s*[<\"]|using\\s+(?:namespace\\s+)?[A-Za-z_]|"
                            + "#!\\s*/(?:usr/)?bin/)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

    private static final Pattern CODE_KEYWORD =
            Pattern.compile("\\b(?:if|else|for|while|switch|return|try|catch|throw|new)\\b");
    private static final Pattern FUNCTION_BLOCK =
            Pattern.compile("\\b[A-Za-z_$][\\w$]*\\s*\\([^\\r\\n)]*\\)\\s*\\{");
    private static final Pattern INDENTED_LINE =
            Pattern.compile("(?m)^\\s{2,}\\S");

    private static final Set<String> SIMPLE_COMMANDS = Set.of(
            "cd", "dir", "ls", "pwd", "cat", "type", "echo", "mkdir", "md", "rmdir", "rd",
            "del", "erase", "copy", "xcopy", "robocopy", "move", "ren", "rename",
            "tasklist", "taskkill", "where", "whoami", "ipconfig", "ping", "tracert",
            "netstat", "netsh", "powercfg", "reg", "sc", "sfc", "dism",
            "curl", "wget", "ssh", "scp", "tar", "7z",
            "java", "javac", "python", "python3", "py", "pip", "pip3",
            "dotnet", "mvn", "mvnw", "gradle", "gradlew",
            "docker", "podman", "kubectl", "helm",
            "winget", "choco", "scoop"
    );

    private static final Set<String> GIT_SUBCOMMANDS = Set.of(
            "add", "bisect", "branch", "checkout", "cherry-pick", "clone", "commit", "diff",
            "fetch", "grep", "init", "log", "merge", "mv", "pull", "push", "rebase", "reset",
            "restore", "revert", "rm", "show", "status", "switch", "tag", "worktree"
    );

    private static final Set<String> PACKAGE_MANAGER_SUBCOMMANDS = Set.of(
            "add", "build", "ci", "create", "exec", "i", "init", "install", "publish", "remove",
            "run", "start", "test", "uninstall", "update", "upgrade"
    );

    private ClipContentClassifier() {}

    public static ClipContentType classify(String content) {
        if (content == null) {
            return ClipContentType.TEXT;
        }

        String value = content.trim();
        if (value.isEmpty()) {
            return ClipContentType.TEXT;
        }

        boolean hasLineBreak = TextValues.containsLineBreak(value);

        if (isUrl(value, hasLineBreak)) return ClipContentType.URL;
        if (isPath(value, hasLineBreak)) return ClipContentType.PATH;
        if (isJson(value)) return ClipContentType.JSON;
        if (isCommand(value)) return ClipContentType.COMMAND;
        if (isCode(value, hasLineBreak)) return ClipContentType.CODE;

        return ClipContentType.TEXT;
    }

    private static boolean isUrl(String value, boolean hasLineBreak) {
        if (value.length() > 4_096 || hasLineBreak || value.indexOf(' ') >= 0) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null) return false;

            String normalized = scheme.toLowerCase(Locale.ROOT);
            if (!normalized.equals("http") && !normalized.equals("https")) return false;

            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isPath(String value, boolean hasLineBreak) {
        if (value.length() > 32_767 || hasLineBreak) return false;

        return WINDOWS_DRIVE_PATH.matcher(value).matches()
                || WINDOWS_UNC_PATH.matcher(value).matches()
                || WINDOWS_ENV_PATH.matcher(value).matches()
                || UNIX_ABSOLUTE_PATH.matcher(value).matches()
                || FILE_URI_PATH.matcher(value).matches();
    }

    private static boolean isJson(String value) {
        if (value.length() > MAX_JSON_SCAN_CHARS) return false;
        if (value.length() < 2) return false;

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (!((first == '{' && last == '}') || (first == '[' && last == ']'))) {
            return false;
        }

        return new JsonValidator(value).isValidDocument();
    }

    private static boolean isCommand(String value) {
        if (value.length() > 32_767) return false;

        String firstLine = firstNonBlankLine(value);
        if (firstLine.isEmpty()) return false;

        if (POWERSHELL_PROMPT.matcher(firstLine).matches()
                || CMD_PROMPT.matcher(firstLine).matches()
                || POWERSHELL_CMDLET.matcher(firstLine).matches()) {
            return true;
        }

        String lower = firstLine.toLowerCase(Locale.ROOT);
        if (lower.startsWith("$env:")
                || lower.startsWith("./")
                || lower.startsWith(".\\")
                || lower.startsWith("sudo ")
                || lower.startsWith("cmd /c ")
                || lower.startsWith("powershell ")
                || lower.startsWith("pwsh ")) {
            return true;
        }

        CommandTokens tokens = commandTokens(lower);
        String command = stripExecutableSuffix(tokens.command());
        if (SIMPLE_COMMANDS.contains(command)) {
            return tokens.hasArgument()
                    || command.equals("pwd")
                    || command.equals("dir")
                    || command.equals("ls");
        }

        if (command.equals("git")) {
            return tokens.hasArgument()
                    && GIT_SUBCOMMANDS.contains(stripPunctuation(tokens.firstArgument()));
        }

        if (command.equals("npm") || command.equals("pnpm") || command.equals("yarn")) {
            return tokens.hasArgument()
                    && PACKAGE_MANAGER_SUBCOMMANDS.contains(
                            stripPunctuation(tokens.firstArgument())
                    );
        }

        return false;
    }

    private static boolean isCode(String value, boolean hasLineBreak) {
        if (CODE_FENCE.matcher(value).find()
                || HTML_OR_XML.matcher(value).find()
                || SQL.matcher(value).find()
                || DECLARATIVE_CODE.matcher(value).find()) {
            return true;
        }

        int score = 0;

        if (value.contains("{") && value.contains("}")) score += 2;
        if (value.contains(";")) score++;
        if (value.contains("=>") || value.contains("->") || value.contains("::")) score++;
        if (value.contains("==") || value.contains("!=") || value.contains("&&") || value.contains("||")) score++;
        if (CODE_KEYWORD.matcher(value).find()) score++;
        if (FUNCTION_BLOCK.matcher(value).find()) score += 2;
        if (hasLineBreak && INDENTED_LINE.matcher(value).find()) score++;

        return score >= 3;
    }

    private static String firstNonBlankLine(String value) {
        int start = 0;
        while (start < value.length()) {
            int end = value.indexOf('\n', start);
            if (end < 0) end = value.length();

            int contentStart = start;
            int contentEnd = end;
            while (contentStart < contentEnd && value.charAt(contentStart) <= ' ') {
                contentStart++;
            }
            while (contentEnd > contentStart && value.charAt(contentEnd - 1) <= ' ') {
                contentEnd--;
            }

            if (contentStart < contentEnd) {
                int carriageReturn = value.indexOf('\r', contentStart);
                if (carriageReturn < 0 || carriageReturn >= contentEnd) {
                    return value.substring(contentStart, contentEnd);
                }

                StringBuilder line = new StringBuilder(contentEnd - contentStart);
                for (int index = contentStart; index < contentEnd; index++) {
                    char ch = value.charAt(index);
                    if (ch != '\r') line.append(ch);
                }
                if (!line.isEmpty()) return line.toString();
            }

            start = end + 1;
        }
        return "";
    }

    private static CommandTokens commandTokens(String value) {
        int firstSeparator = indexOfCommandWhitespace(value, 0);
        if (firstSeparator < 0) {
            return new CommandTokens(value, "", false);
        }

        int argumentStart = firstSeparator;
        while (argumentStart < value.length()
                && isCommandWhitespace(value.charAt(argumentStart))) {
            argumentStart++;
        }
        if (argumentStart >= value.length()) {
            return new CommandTokens(value.substring(0, firstSeparator), "", false);
        }

        int argumentEnd = indexOfCommandWhitespace(value, argumentStart);
        if (argumentEnd < 0) argumentEnd = value.length();

        return new CommandTokens(
                value.substring(0, firstSeparator),
                value.substring(argumentStart, argumentEnd),
                true
        );
    }

    private static int indexOfCommandWhitespace(String value, int start) {
        for (int index = start; index < value.length(); index++) {
            if (isCommandWhitespace(value.charAt(index))) return index;
        }
        return -1;
    }

    private static boolean isCommandWhitespace(char ch) {
        return ch == ' '
                || ch == '\t'
                || ch == '\n'
                || ch == '\u000B'
                || ch == '\f'
                || ch == '\r';
    }

    private static String stripExecutableSuffix(String token) {
        String cleaned = stripPunctuation(token);
        if (cleaned.endsWith(".exe")) cleaned = cleaned.substring(0, cleaned.length() - 4);
        if (cleaned.endsWith(".bat")) cleaned = cleaned.substring(0, cleaned.length() - 4);
        if (cleaned.endsWith(".cmd")) cleaned = cleaned.substring(0, cleaned.length() - 4);
        return cleaned;
    }

    private static String stripPunctuation(String token) {
        int end = token.length();
        while (end > 0) {
            char ch = token.charAt(end - 1);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.') break;
            end--;
        }
        return token.substring(0, end);
    }

    private record CommandTokens(
            String command,
            String firstArgument,
            boolean hasArgument
    ) {}

    /**
     * Small validating JSON parser used only for classification. It accepts the
     * complete JSON grammar and rejects trailing text, malformed escapes, and
     * invalid numbers without creating a parsed object tree.
     */
    private static final class JsonValidator {
        private static final int MAX_DEPTH = 128;

        private final String source;
        private int index;

        private JsonValidator(String source) {
            this.source = source;
        }

        private boolean isValidDocument() {
            try {
                skipWhitespace();
                parseValue(0);
                skipWhitespace();
                return index == source.length();
            } catch (InvalidJson ignored) {
                return false;
            }
        }

        private void parseValue(int depth) {
            if (depth > MAX_DEPTH) fail();
            skipWhitespace();
            if (index >= source.length()) fail();

            char ch = source.charAt(index);
            switch (ch) {
                case '{' -> parseObject(depth + 1);
                case '[' -> parseArray(depth + 1);
                case '"' -> parseString();
                case 't' -> consumeLiteral("true");
                case 'f' -> consumeLiteral("false");
                case 'n' -> consumeLiteral("null");
                default -> {
                    if (ch == '-' || isDigit(ch)) parseNumber();
                    else fail();
                }
            }
        }

        private void parseObject(int depth) {
            expect('{');
            skipWhitespace();
            if (consumeIf('}')) return;

            while (true) {
                skipWhitespace();
                parseString();
                skipWhitespace();
                expect(':');
                parseValue(depth);
                skipWhitespace();

                if (consumeIf('}')) return;
                expect(',');
            }
        }

        private void parseArray(int depth) {
            expect('[');
            skipWhitespace();
            if (consumeIf(']')) return;

            while (true) {
                parseValue(depth);
                skipWhitespace();

                if (consumeIf(']')) return;
                expect(',');
            }
        }

        private void parseString() {
            expect('"');

            while (index < source.length()) {
                char ch = source.charAt(index++);

                if (ch == '"') return;
                if (ch < 0x20) fail();

                if (ch == '\\') {
                    if (index >= source.length()) fail();
                    char escaped = source.charAt(index++);

                    if (escaped == 'u') {
                        for (int i = 0; i < 4; i++) {
                            if (index >= source.length() || Character.digit(source.charAt(index++), 16) < 0) {
                                fail();
                            }
                        }
                    } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
                        fail();
                    }
                }
            }

            fail();
        }

        private void parseNumber() {
            consumeIf('-');

            if (consumeIf('0')) {
                if (index < source.length() && isDigit(source.charAt(index))) fail();
            } else {
                requireDigitOneToNine();
                while (index < source.length() && isDigit(source.charAt(index))) index++;
            }

            if (consumeIf('.')) {
                requireDigit();
                while (index < source.length() && isDigit(source.charAt(index))) index++;
            }

            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                    index++;
                }
                requireDigit();
                while (index < source.length() && isDigit(source.charAt(index))) index++;
            }
        }

        private void requireDigitOneToNine() {
            if (index >= source.length()) fail();
            char ch = source.charAt(index);
            if (ch < '1' || ch > '9') fail();
            index++;
        }

        private void requireDigit() {
            if (index >= source.length() || !isDigit(source.charAt(index))) fail();
            index++;
        }

        private boolean isDigit(char ch) {
            return ch >= '0' && ch <= '9';
        }

        private void consumeLiteral(String literal) {
            if (!source.startsWith(literal, index)) fail();
            index += literal.length();
        }

        private void skipWhitespace() {
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') index++;
                else break;
            }
        }

        private boolean consumeIf(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consumeIf(expected)) fail();
        }

        private void fail() {
            throw InvalidJson.INSTANCE;
        }
    }

    private static final class InvalidJson extends RuntimeException {
        private static final InvalidJson INSTANCE = new InvalidJson();

        private InvalidJson() {
            super(null, null, false, false);
        }
    }
}
