/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.privacy;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable local-only rules for suppressing newly copied sensitive text.
 *
 * Defaults preserve historical XClip behavior: every value is captured. A rule
 * blocks capture only after the user explicitly selects SKIP. Existing history
 * is never inspected, rewritten, or deleted by this policy.
 */
public record SensitiveContentPolicy(
        RuleAction paymentCardAction,
        RuleAction oneTimeCodeAction
) {

    private static final Pattern PAYMENT_CARD_CANDIDATE = Pattern.compile(
            "(?<![A-Za-z0-9])(?:\\d[ -]?){12,18}\\d(?![A-Za-z0-9])"
    );
    private static final Pattern OTP_CANDIDATE = Pattern.compile(
            "(?<!\\d)\\d{4,8}(?!\\d)"
    );
    private static final Pattern OTP_CONTEXT = Pattern.compile(
            "(?iuU)(?:\\botp\\b|\\b2fa\\b|\\bone[- ]?time(?: password| passcode| code)?\\b|"
                    + "\\bverification code\\b|\\bsecurity code\\b|"
                    + "\\bauthentication code\\b|\\blogin code\\b|\\bsign[- ]?in code\\b|"
                    + "\\bкод подтверждения\\b|\\bодноразовый код\\b|"
                    + "\\bкод безопасности\\b|\\bкод входа\\b|"
                    + "\\btasdiqlash kodi\\b|\\bbir martalik kod\\b)"
    );

    private static final int OTP_CONTEXT_RADIUS = 96;

    public SensitiveContentPolicy {
        paymentCardAction = Objects.requireNonNull(paymentCardAction, "paymentCardAction");
        oneTimeCodeAction = Objects.requireNonNull(oneTimeCodeAction, "oneTimeCodeAction");
    }

    public static SensitiveContentPolicy defaults() {
        return new SensitiveContentPolicy(RuleAction.CAPTURE, RuleAction.CAPTURE);
    }

    public boolean empty() {
        return paymentCardAction == RuleAction.CAPTURE
                && oneTimeCodeAction == RuleAction.CAPTURE;
    }

    public boolean allowsCapture(String content) {
        return firstBlockedKind(content).isEmpty();
    }

    public Optional<SensitiveKind> firstBlockedKind(String content) {
        if (content == null || content.isBlank() || empty()) return Optional.empty();

        Set<SensitiveKind> kinds = detect(content);
        if (paymentCardAction == RuleAction.SKIP
                && kinds.contains(SensitiveKind.PAYMENT_CARD)) {
            return Optional.of(SensitiveKind.PAYMENT_CARD);
        }
        if (oneTimeCodeAction == RuleAction.SKIP
                && kinds.contains(SensitiveKind.ONE_TIME_CODE)) {
            return Optional.of(SensitiveKind.ONE_TIME_CODE);
        }
        return Optional.empty();
    }

    public static Set<SensitiveKind> detect(String content) {
        if (content == null || content.isBlank()) return Set.of();

        EnumSet<SensitiveKind> result = EnumSet.noneOf(SensitiveKind.class);
        if (containsPaymentCard(content)) result.add(SensitiveKind.PAYMENT_CARD);
        if (containsContextualOneTimeCode(content)) result.add(SensitiveKind.ONE_TIME_CODE);
        return Set.copyOf(result);
    }

    static boolean containsPaymentCard(String content) {
        Matcher matcher = PAYMENT_CARD_CANDIDATE.matcher(content);
        while (matcher.find()) {
            String digits = digitsOnly(matcher.group());
            if (digits.length() < 13 || digits.length() > 19) continue;

            char leading = digits.charAt(0);
            if (leading < '2' || leading > '6') continue;
            if (allCharactersEqual(digits)) continue;
            if (passesLuhn(digits)) return true;
        }
        return false;
    }

    static boolean containsContextualOneTimeCode(String content) {
        Matcher codeMatcher = OTP_CANDIDATE.matcher(content);
        while (codeMatcher.find()) {
            int start = Math.max(0, codeMatcher.start() - OTP_CONTEXT_RADIUS);
            int end = Math.min(content.length(), codeMatcher.end() + OTP_CONTEXT_RADIUS);
            String context = content.substring(start, end);
            if (OTP_CONTEXT.matcher(context).find()) return true;
        }
        return false;
    }

    static boolean passesLuhn(String digits) {
        if (digits == null || digits.isEmpty()) return false;

        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            char value = digits.charAt(index);
            if (value < '0' || value > '9') return false;

            int digit = value - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static String digitsOnly(String value) {
        StringBuilder digits = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch >= '0' && ch <= '9') digits.append(ch);
        }
        return digits.toString();
    }

    private static boolean allCharactersEqual(String value) {
        char first = value.charAt(0);
        for (int index = 1; index < value.length(); index++) {
            if (value.charAt(index) != first) return false;
        }
        return true;
    }

    public enum RuleAction {
        CAPTURE,
        SKIP
    }

    public enum SensitiveKind {
        PAYMENT_CARD,
        ONE_TIME_CODE;

        public String stableName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
