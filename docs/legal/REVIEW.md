# Review of the Lowkey legal drafts

> **Draft, not legal advice.** This note records a review of the three drafts in this folder. It is not a legal opinion. A Belgian lawyer still has to read the drafts before they are published, linked from Google Play, or shown in the app. No street address, enterprise number, VAT number, DUNS number, or personal name, email or phone of a founder is in these files. Identity fields are [SQUARE BRACKETS] on purpose.

The three drafts were written from the public source, then read again from four angles: consumer-law compliance, GDPR and Play Data safety accuracy, plain language, and consistency across the three documents. The files in this folder are the text after that revision. This note says what was checked, what the briefing got wrong, what each pass changed or questioned, where the passes disagreed, and what is still open.

## What was checked

Behaviour was taken from the source at Lowkey 0.15.2 (`629aa3c` on `main`), not from the briefing alone:

- `README.md`, `docs/user-guide.md`, `CONTRIBUTING.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/rheerkens/androidkeyboard/models/ModelCatalog.kt`
- `app/src/main/java/com/rheerkens/androidkeyboard/companion/screens/AboutScreen.kt` and `DiagnosticsScreen.kt`
- `app/src/main/java/com/rheerkens/androidkeyboard/ai/` (`AiClient`, `AiConnection`, `AiActions`, `ImageApi`, `PromptApi`, `SecretStore`, `InputImages`)
- `app/src/main/java/com/rheerkens/androidkeyboard/updates/AndroidUpdates.kt` and `UpdateFeed.kt`
- `app/src/main/java/com/rheerkens/androidkeyboard/dictionary/KeyboardLanguages.kt`, `LanguagePacks.kt`, and `assets/dict/languages.txt`
- `app/src/main/java/com/rheerkens/androidkeyboard/diagnostics/CrashLog.kt` and `SessionLog.kt`
- `app/src/main/java/com/rheerkens/androidkeyboard/clipboard/RecentScreenshots.kt`
- `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`
- `app/build.gradle.kts` (application id, version, update-feed URL)

Network calls in the app code are only: speech-model downloads, word-list downloads, the update feed and update APK, and experimental AI (including a follow-up download if the provider returns an image link). There is no billing library, no analytics SDK, and no crash uploader.

## Corrections to the briefing

These were wrong, or too strong, and the drafts follow the source.

| Briefing | What the source shows |
| --- | --- |
| Moderation checks may also go to the AI provider | There is no moderation endpoint. Create, edit and Improve are the only AI calls. The provider may refuse the same request under its own safety rules, and the app shows that error. |
| "No data ever leaves the device" | Rejected. Downloads, the update check, and optional AI all leave the phone. The drafts list them. |
| Op Studio receives no user data | True of the app: it has no first-party backend. False of a support mailbox, once someone writes to it. The privacy policy says an email you send is received. Crash reports are local until the user copies one. |
| Word lists from "AOSP" | The phone downloads them from `android.googlesource.com`, pinned to one commit in `languages.txt`. English (US) is bundled and is not downloaded. |
| Speech models from Hugging Face | Confirmed. `huggingface.co`, pinned commits, HTTPS. A delivery network in front of Hugging Face can also see the connection. The policy says so without naming a CDN the code does not name. |
| Package `com.opstudio.lowkey` | That is the id Op Studio intends to publish. The source still uses `com.rheerkens.androidkeyboard`. `UpdateFeed.acceptsReleaseUpdates` runs the GitHub check only for that current id. |
| The app localizes to Dutch | It does not. The user guide says the interface is English. Keyboard languages include Dutch. The Dutch in-app text is a draft for a future interface, and the notice says not to ship it yet. |
| API keys stored locally | Confirmed. Android Keystore, AES-GCM, not in the export file, not in Android backup. The settings screen shows the last four characters. The server address is a setting and can be in the export file. The key is not. |
| Mic only while dictating | Confirmed by the manifest and the voice path. Audio is not written to a file. Voice is off in password and private fields. Debug builds can play bundled clips instead of the microphone. That is omitted from the consumer text because it is not the published app. |
| Optional photo access | Confirmed. Recent screenshots need the photos permission (or storage on older Android). The switch defaults to on, and nothing is listed until permission is granted. The photo picker does not need that permission. Screenshots are uploaded only if the user attaches one to an AI prompt. |

Other facts the drafts rely on, which the briefing did not spell out:

- The update check is an HTTPS GET of `update.properties` on GitHub, about once a day, and when the app is opened if the last check is older than about six hours. The request sets `User-Agent` to `Lowkey/` plus the version name. Model and word-list downloads do not set that header.
- The update APK downloads only after the user taps Update.
- Improve sends the prompt and a picture count, not the pictures. Create sends up to four pictures, each reduced to at most 1,536 pixels on the long side.
- If the provider returns an `https` image link, the phone downloads it without the API key.
- Release builds require HTTPS for the AI server. A debug build may use plain HTTP only to localhost.
- Android backup and device transfer of app data are excluded. "Learn from my typing" is off by default and is stored in the no-backup directory.
- Clipboard history, crash reports and the voice-session log stay on the phone. The session log stores a word count and step names, not the dictated sentence. A crash report can include manufacturer and model.
- There is no account, no ad id, and no in-app purchase. ONNX Runtime's Java telemetry transport is excluded at build time (`CONTRIBUTING.md`).
- The README still calls Lowkey a personal project and says it is not on the Play Store. These drafts are written for publication by Op Studio, a Belgian company, including as Play trader terms. They do not say the Play listing already exists.

The support placeholder is written `[support @ opstudio.eu]`, with spaces, in every draft. The intended published mailbox is the local part `support` at `opstudio.eu`. The spaces are there so this public repository does not contain an email-shaped string. Put the normal spelling on the published page only after the mailbox is real.

Directive (EU) 2024/2853, Article 2(1), as corrected in the Official Journal of 7 May 2026 (2026/90364), applies to products placed on the market or put into service after 8 December 2026. Article 15 says that liability cannot be limited or excluded by contract towards the injured person. Article 2(2) is the free-and-open-source exception for software developed or supplied outside a commercial activity. The terms cite that and do not rely on the exception.

## Pass 1 — consumer-law compliance

Questioned:

- A warranty disclaimer copied from the Apache License would read as the consumer's only remedy. The Apache text stays in the licence section. The liability section is a separate, narrower limit, and it leads with what cannot be limited: intent, fraud, gross negligence, death and personal injury, and rights a consumer cannot waive. Directive 93/13/EEC and Book VI of the Belgian Code of Economic Law are named. Article numbers inside Book VI are not, because a wrong number is worse than none.
- The first liability sentence also covered contributors. That looked like Op Studio was waiving claims against people who are not the trader. The published text limits only Op Studio. Contributors stay under the Apache License, and the terms say that licence does not cut down non-waivable rights against the publisher.
- A sentence that asked a court to shrink an unfair term to the widest limit still allowed was removed. An unfair term is not binding. The rest of the terms stay. Saving a maximum waiver is the sort of clause Directive 93/13 is aimed at.
- "By continuing to use the app you agree" is not used. A change that reduces a consumer's rights needs a legal basis or a real agreement. Typing on is not that agreement.
- Exclusive jurisdiction against consumers is not used. Belgian law is chosen, with the mandatory rules and courts of the consumer's residence left in place.
- The product-liability section states the 8 December 2026 date, Article 15, and that a free Play listing by a company can still be commercial supply. It does not conclude that the open-source exception fails. It says the terms do not rely on it.
- Directive (EU) 2019/770 is not declared in or out of scope. The terms say statutory conformity and withdrawal rights, where they apply, are not waived.
- No remote kill switch is claimed. The app has none. There is no account to close.
- The old EU ODR platform is mentioned only to say it is not cited. It was discontinued in 2025.
- Trader identity is a table of placeholders, including phone, because Play's trader form asks for contact details. No real details were invented to make the table look finished.

Left in place after the pass: the "as available" limit on Op Studio, the warning that suggestions and transcription can be wrong, and the statement that AI charges are the provider's. Those are descriptions, and they sit under the non-waiver.

## Pass 2 — GDPR and Play Data safety, against the app

Questioned:

- "Op Studio receives no user data" could not survive a support email or a crash report the user pastes into one. Both drafts now split "the app does not send this" from "an email you send is received". Retention of that mailbox is `[SUPPORT MAILBOX RETENTION AND ACCESS]` because the app has no ticket system to describe.
- Calling Op Studio "not a controller" would decide the open question. The policy describes on-device processing, says there is usually no copy at Op Studio, offers the contact, and does not pick a legal role.
- Saying standard contractual clauses are in place would be false. Saying no transfer rule applies would also be a legal conclusion. The policy says the phone connects directly to those hosts and Op Studio does not get their logs through the app.
- A Play Data safety form was not filled in. Google's words "collected" and "shared" are not the GDPR's words. The map below is for the person who fills in the form. It is not an answer key.

| Topic | What the app does | Easy mistake on the form |
| --- | --- | --- |
| Typing, dictionary, predictions, clipboard | On the phone. The keyboard can see the focused field. | Marking this as collected by the developer |
| Voice | Microphone only while dictating. Audio not saved. Transcript inserted locally. | Marking audio as collected |
| Photos | Optional local read of recent screenshots. Upload only if the user attaches one to AI. | Marking photos as collected by Op Studio |
| Diagnostics | On the phone, until the user copies them. Crash text can include device model. | Marking crash logs as sent automatically |
| App activity / analytics | No analytics SDK and no ad id | Declaring "no data shared" while downloads and optional AI exist |
| Downloads | IP address and the file URL are visible to Hugging Face, `android.googlesource.com`, and GitHub | Treating a transient IP as either "we collect device IDs" or "nothing leaves" without reading Google's current definition |
| Experimental AI | User-initiated. Prompt, optional images, and the key go to the provider the user chose. An image link may be fetched from another HTTPS host. | Ticking "data is not shared with third parties" if Google counts a user-chosen provider |
| Account | None in the app. Play's own account, if the user installs from Play, is Google's. | Implying Op Studio receives the Play account |

The in-app notice was too easy to read as "internet is used, but nobody sees it". One sentence was added, in English and Dutch: the download hosts and a chosen AI provider can see the connection.

Moderation stayed corrected. A separate moderation call is not documented, because it is not in the code. If a later build adds one, the policy and the notice both have to change.

## Pass 3 — plain language

Questioned:

- The first privacy draft pointed readers at this review file, and it said the controller question and the children's question were "not decided in this draft". That is lawyer talk on a page a person is meant to read. Those sentences were removed from the policy. The questions are in this note. The policy still says what the app does and where to write.
- The terms said Lowkey "is not on Google Play at the time of this draft", and they told the reader the open-source exception was "not a gap these terms try to open" in a more defensive voice. The Play-status sentence was removed so a later copy-paste onto the live page is not immediately false. The product-liability section still says the terms do not rely on the exception.
- The liability section opened on the carve-outs, then the limit. That order stayed. It is the difference between a waiver and a limited clause.
- The in-app notice is one block for About and first run, so a shorter first-run cut cannot drop the AI sentence. It is long for a first-run card. Cutting it was rejected.
- The Dutch block is labelled "do not ship". It tracks the English sentences, including the image-link download and the "hosts can see the connection" sentence.

Left in the consumer text on purpose, despite the tone: the package-id gate on the updater, and the instruction that a renamed build does not contact GitHub until that gate changes. Hiding that would make the policy false for `com.opstudio.lowkey` as the code stands.

## Pass 4 — consistency and gaps

Checked that all three documents use the same URLs (`https://opstudio.eu/lowkey/privacy`, `https://opstudio.eu/lowkey/terms`), the same support placeholder, the same effective-date placeholder, the same package names, and the same list of what leaves the phone (models, word lists, update check and download, optional AI, image-link fetch).

The terms do not keep a second data inventory. They point at the privacy policy. The notice is a short form of that inventory, not a different one.

Gaps that are still in the text, and should stay visible:

- The updater section is true for the current package id and may be wrong for the Play build. The privacy policy says so. The terms refuse to promise that every build checks GitHub. Before publication, delete or rewrite that section to match the build that will actually ship. Google Play also restricts apps that download executable code from outside Play. That is a product decision. These drafts do not pretend it has been made.
- The README (personal project, not on Play, Apache warranty tone) does not match these drafts. Publishing the company terms without updating the README will contradict them. This branch does not edit the README. That was out of scope.
- A new package name does not migrate data from the retired package. The privacy policy says so in one sentence. The notice does not. People who still have the old package will not see the new notice until that app shows it.
- The notice has no postal address. The full policy and the terms do, as a placeholder. A lawyer may want the address on the notice as well. It was left off so the notice stays one screen and so this file does not grow a real address later by mistake.
- "Save to Gallery", the export file, and paste-into-another-app are in the privacy policy only. They are user-initiated copies, not Op Studio collection. The notice does not list them.
- Debug-only localhost HTTP is not in the consumer text.

## Where the passes disagreed

**How much of the updater to put on the public page.** Accuracy wanted the package-id gate in the policy, because otherwise `com.opstudio.lowkey` would be described as phoning GitHub when the code will not. Plain language wanted that sentence in this note only. Accuracy won. The sentence stays in the privacy policy. The terms mention the exception in one line and do not repeat the id.

**Whether to call the Apache disclaimer the warranty.** Consumer law wanted it quarantined. Plain language wanted one sentence a person already sees in the app ("no warranty"). The terms keep the Apache disclaimer as a statement about the source licence, then say it does not reduce non-waivable rights against the publisher. That is a compromise, and a lawyer may still want the consumer page to avoid the words "no warranty" entirely.

**Whether "not a controller" is the honest GDPR line.** It matches the instinct of "we never see the data". It does not match the possibility that designing on-device processing, or causing a phone to contact Hugging Face or an AI provider, is still processing the publisher determines. The policy refuses the label. The disagreement is unresolved and is the first open question below.

**Dutch in the notice.** The request asked for Dutch if the app localizes. It does not. Consistency wanted no Dutch, so nobody pastes it into an English-only UI. The Dutch is in the file, under a heading that says not to ship it, so a later translation has a starting point. If that is too easy to misuse, delete the Dutch block before the file is handed to whoever edits the app.

**Length of the first-run notice.** Plain language wanted three lines. Data-safety accuracy wanted downloads, updates, AI, the image link, and the fact that hosts see the connection. The longer text won. A first-run layout that cannot show it should link to the privacy policy and keep at least the AI sentence and the download sentence. Do not replace it with "your data stays on your phone".

## Open questions for a Belgian lawyer

These are questions, not answers. The drafts are written so they stay accurate if the lawyer picks either side, except where a question says a sentence must change.

1. **GDPR controller.** For on-device typing, voice, the dictionary and diagnostics, is Op Studio a controller even though the contents never reach it? For the downloads, is Op Studio a controller of the IP address the phone exposes to Hugging Face, `android.googlesource.com` and GitHub, or are those hosts separate controllers of their own logs? For experimental AI, is the user the sole decision-maker because they bring their own key and choose the server, or does Op Studio determine the means by building the client? Is there any joint controllership with those hosts?
2. **Transparency duties if the role is "controller" and the data never arrives.** Is the privacy policy plus the in-app notice enough for Articles 13 and 14, or is a layered notice inside the system keyboard permission screen also required? The app does not control Android's keyboard-enable dialog.
3. **Support mail.** Once `[support @ opstudio.eu]` is a real mailbox, what is the retention, who can read it, and is that the Article 13 contact? Is a separate privacy contact required? What is said to someone who emails a crash report that contains a device model and a stack trace?
4. **International transfers.** When the phone connects directly to a host outside the EEA, what is Op Studio's transfer role, if any? The drafts do not claim that standard contractual clauses exist.
5. **Directive (EU) 2019/770.** Does it apply to a free app where the trader does not receive personal data as payment for the app? Downloads expose an IP address to third parties. Optional AI sends data to a provider the user chose, not to Op Studio. Which of those, if any, is "data provided as consideration"? If the directive applies, which conformity and remedy sentences must be added, and is there a withdrawal right for this install?
6. **Product Liability Directive (EU) 2024/2853 and the commercial-activity test.** Does publishing on Google Play under a company, free of charge, mean the app is supplied in the course of a commercial activity, so Article 2(2) does not apply? Does the same test apply to signed APKs on GitHub? The terms do not rely on the exception. Confirm the consolidated date (Article 2(1) after 8 December 2026, corrigendum of 7 May 2026) against the transitional article, so a one-day difference is not missed. Confirm that Article 15 is correctly described.
7. **Cyber Resilience Act, Regulation (EU) 2024/2847.** The same "outside a commercial activity" idea appears there for free and open-source software. Does a Play listing by a company fail that test? What support period and vulnerability-handling duties would follow, and do these terms need a sentence about security updates? No CRA duties are stated in the drafts.
8. **Trader status.** Is Op Studio a trader towards Play users under Belgian and EU consumer law, including when the app is free and the source is public? What must appear on the Play listing (legal name, address, email, phone, enterprise number), and which of those must also appear in the terms? Do not put the real values in the public git branch.
9. **Language.** The app and these drafts are in English. Belgium has Dutch, French and German. Must the Play listing, the terms, or the privacy policy be available in Dutch or French before consumers in those language regions are bound? The Dutch notice is not a translation of the full policy.
10. **Unfair terms, in detail.** Is the remaining limit on Op Studio's liability (lost text, wrong transcription, AI charges, reliance on an image) still an unfair term in a free consumer app, even with the carve-outs? Which items of the Book VI blacklist should be cited by number? Under Book 6 of the Civil Code, which extra-contractual liability cannot be limited (intent, gross negligence, others)?
11. **Children.** The app is not directed at children and has no age gate. What age of digital consent applies in Belgium, and does a keyboard a parent installs count as an information-society service offered to a child? Is a Play "not designed for children" declaration consistent with this text?
12. **Play Data safety and Play policy.** Using the map above and Google's current help text, which rows are "collected" or "shared"? May the Play build keep the GitHub APK installer? If it must be removed, delete the updater section before the policy is linked.
13. **Insurance and product liability in practice.** For software placed on the market after 8 December 2026, does the lawyer want any operational step (insurance, incident contact) mentioned? The drafts do not mention insurance.
14. **The retired package.** People who keep `com.rheerkens.androidkeyboard` have a different install. Do they need this policy, a shorter legacy note, or nothing, once the new id ships?
15. **Governing law for non-consumers.** The placeholder `[COURTS FOR NON-CONSUMER DISPUTES]` is empty. Which court, and is a company user who downloads a free keyboard a consumer anyway?
16. **Mediation.** Is naming the Consumer Mediation Service useful, or should the terms stay silent and let Play's own process sit beside them? No mediation is mandatory in the draft.

## What was not done

No app code, package rename, or build change. No Play Console work. No pull request. The drafts are not published on opstudio.eu. Placeholders are still placeholders.
