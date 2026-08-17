# Security Policy

XClip handles clipboard contents and local persistence, so security and privacy reports are taken seriously.

## Supported versions

| Version | Supported |
|---|---|
| 1.4.x | Yes |
| 1.3.x and older | No |

Users should reproduce security issues against the latest published release before reporting when possible.

## Reporting a vulnerability

**Do not open a public GitHub issue for an unpatched vulnerability.**

Preferred reporting path:

1. If the repository exposes GitHub's **Report a vulnerability** control, use it to create a private security report.
2. Otherwise, contact the maintainer privately via Telegram: `@End1essspace`.

Include enough information to reproduce and assess the issue:

- affected XClip version;
- Windows version and installation type;
- affected component or workflow;
- reproduction steps or a minimal proof of concept;
- expected versus actual security boundary;
- impact and prerequisites;
- any proposed mitigation, if known.

Do not send real passwords, tokens, payment information, private clipboard history, or other unnecessary sensitive user data. Use synthetic examples.

## Relevant security areas

Examples of issues appropriate for private reporting include:

- unintended command or executable execution;
- bypass of local privacy/sensitive-content controls;
- unsafe handling of backup or restore input;
- unauthorized access to or disclosure of local clipboard data;
- path traversal or unsafe file operations;
- security-relevant Windows integration behavior;
- dependency or packaging issues that create a practical exploit path in XClip.

Ordinary crashes, UI bugs, performance problems, and non-security feature requests should use the public issue templates instead.

## Disclosure

Please allow time for investigation and remediation before public disclosure. When a report is confirmed, the maintainer will coordinate a fix and release as appropriate. Public details should be published only after affected users have a reasonable path to an updated version.
