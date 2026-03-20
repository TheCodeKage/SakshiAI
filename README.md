# SaakshiAI 🔒
### *Because silence was never a choice.*

> **Saakshi** (साक्षी) — Hindi for *witness*. Not a judge. Not a reporter. The one who was there, who remembered, and refused to let it be erased.

---

## What It Is

SaakshiAI is a private, on-device voice documentation companion for survivors of domestic abuse, workplace harassment, and coercive control.

A survivor speaks. SaakshiAI listens — not to a server, not to a company, but to the device in their hand and nowhere else. Their account is transcribed, structured into a timestamped legal-ready record, and stored encrypted on-device. Nothing is ever transmitted. The app is architecturally incapable of leaking data because it never has the data to begin with.

---

## The Problem

Most survivors never report what happened to them. Not because it didn't happen — but because every place they could record it feels dangerous.

- He checks her phone
- It's a shared account
- Cloud apps leave server logs
- A lawyer once told her that digital records can be subpoenaed

So they stay silent. And silence, over time, becomes the abuser's greatest ally.

SaakshiAI is built for that silence.

---

## How It Works

1. **Speak** — Open the app. No login, no account, no name collected. Press record and speak freely, in any language, however fragmented.
2. **Structure** — The on-device AI (powered by RunAnywhere SDK + Qwen 2.5 0.5B) transcribes and structures the account: what happened, who was involved, whether threats or physical contact occurred, severity level.
3. **Store** — Saved encrypted on the device using Android Keystore. Never uploaded. Never synced.
4. **Build a record** — Over time, entries form a timeline — not a diary, but a case file. The kind a lawyer can read in ten minutes.
5. **Export when ready** — One tap generates a clean, structured, timestamped PDF. Only when she chooses.

---

## Why Local AI Is Not Optional

This is not a privacy preference. It is a threat model.

If voice recordings are sent to a server → they can be subpoenaed.  
If network requests are made → an abuser monitoring the router will find them.  
If cloud inference is used → a shared family plan exposes the data.  

The AI **must** run on-device. RunAnywhere SDK makes this possible on low-end Android hardware with no internet dependency.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Platform | Android (Kotlin + Jetpack Compose) |
| On-device AI | RunAnywhere SDK |
| Speech-to-Text | RunAnywhere STT (on-device) |
| Language Model | Qwen 2.5 0.5B Instruct Q6_K |
| Local Storage | SQLite + Android Keystore (AES-256) |
| Export | PDF generation (on-device) |
| Network usage | **Zero** |

---

## Safety Features

- **No login or account** — nothing to find if someone checks the phone
- **Decoy screen** — wrong PIN opens a decoy utility screen
- **No app notifications** — no lock screen alerts that reveal usage
- **Stealth naming** — app can be renamed to appear as a utility
- **Zero network indicator** — visible proof that nothing leaves the device

---

## What The AI Does

Given a raw spoken testimony, the on-device model produces a structured incident record containing:

- **Date & time** of the entry
- **Incident type** (physical contact, verbal threat, coercive control, etc.)
- **People involved**
- **Direct threats documented** (verbatim where possible)
- **Witnesses present**
- **Pattern flag** — detects repeat language ("again", "like last time")
- **Severity tag** — Documentation Only / Concerning Pattern / Immediate Risk

---

## What It Does Not Do

- SaakshiAI is **not a crisis helpline**. If a user is in immediate danger, the app surfaces emergency contacts (112 in India).
- SaakshiAI is **not a replacement for legal counsel**. It creates documentation, not legal strategy.
- SaakshiAI is **not a therapist**. It listens and structures. It does not counsel.

---

## Built At

**HackXtreme** — GGSIPU × Microsoft Noida  
4-person team | 5 days | Android | RunAnywhere SDK

---

## Team

*[Your team names here]*

---

*"We didn't build an app. We built a witness."*
