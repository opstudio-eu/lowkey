# Privacy policy for Lowkey

> **Draft, not legal advice.** This is a draft for review by a Belgian lawyer before anyone publishes it. It is not legal advice, and it is not in force. Text in [SQUARE BRACKETS] is a placeholder. Replace or delete every placeholder before publication. Do not add a street address, enterprise number, VAT number, DUNS number, or a personal name, email or phone of a founder to this file. Publish the support mailbox as a normal address only after Op Studio confirms that the mailbox exists.

**Effective date:** [EFFECTIVE DATE]

This policy describes Lowkey, the Android keyboard published by Op Studio. The public source of the app is at <https://github.com/opstudio-eu/lowkey>, under the Apache License 2.0.

The page for this policy, once it is published, is <https://opstudio.eu/lowkey/privacy>. The terms of use are at <https://opstudio.eu/lowkey/terms>.

## The short version

Lowkey is a keyboard. Typing, suggestions, autocorrect, your dictionary, next-word predictions and voice transcription run on your phone.

The app does use the internet. It downloads speech models and extra keyboard languages when you ask it to, it can check for an app update, and it downloads that update if you tap Update. If you turn on the experimental AI feature, the prompt and any pictures you submit go from your phone to the AI provider you choose, with a key you supply.

Op Studio does not run a server that receives what you type, what you say, or the files the app keeps. There is no account, no advertising, and no analytics in the app. The organisations that deliver a download, and an AI provider you choose, can see the connection from your phone, including its IP address. If you email the support address, Op Studio receives that email. The app does not send it for you.

## Who we are

Lowkey is published by Op Studio (also written OpStudio), a company in Belgium.

| | |
| --- | --- |
| Legal name | [LEGAL NAME] |
| Legal form | [LEGAL FORM] |
| Enterprise number (KBO/BCE) | [ENTERPRISE NUMBER] |
| Registered office | [REGISTERED OFFICE ADDRESS] |
| VAT number | [VAT NUMBER] |
| Phone (trader contact) | [TRADER PHONE NUMBER] |
| Support | [support @ opstudio.eu] |

In the app and on the trademark, the name is written OpStudio. "Op Studio" and "OpStudio" are the same publisher.

This draft does not include a real address, registration number or personal contact. Those belong in the published page only after a lawyer has checked them.

## Which app this policy covers

The app Op Studio intends to publish is the Android package `com.opstudio.lowkey`. It replaces the retired package `com.rheerkens.androidkeyboard`.

This text was checked against the public source of Lowkey 0.15.2, which still uses the package name `com.rheerkens.androidkeyboard`. The behaviour below is that source. The package rename is a separate change. If a later build removes a network feature described here, this policy has to be updated before it is published.

Android treats a new package name as a different app. Installing `com.opstudio.lowkey` does not, by itself, move the dictionary, settings or models of an older package.

## What the keyboard can see

Lowkey is an input method: the keyboard Android shows when you type. To do that job it can read the text in the field you are typing in, and it receives the keys you press. That is how suggestions, autocorrect and predictions work.

That text stays on the phone, except in the cases under "What leaves your phone". In a password field, or a field that asks keyboards not to learn (such as a private or incognito field):

- voice input is not offered
- the experimental AI control is hidden (it is also hidden in number fields)
- Lowkey does not learn predictions from what you type
- clipboard history is hidden, and a clip you copy while such a field is focused is not kept

Email and web-address fields are also left out of prediction learning. Double-space-to-period does not run in password, address, numeric or private fields.

## What stays on your phone

These are processed on the device. Op Studio does not receive them from the app.

**Typing, suggestions and autocorrect.** Suggestions and autocorrect use word lists on the phone. English (US) is built in. Other languages are downloaded when you add them, then used offline. The app can look at up to 2,048 characters before the cursor to match a phrase you just typed. It does not store that passage for that feature.

**Your dictionary and speech rules.** Saved words and optional spoken-phrase rules stay in the app. You can export them yourself. See "Copies you make".

**Predictions you choose to turn on.** "Learn from my typing" is off until you turn it on. It counts which words you type after which one or two words, on the phone, in storage that Android backup does not include. It fades over time. "Forget what Lowkey learned" deletes it. Your saved words stay.

**Voice.** The microphone is used only while you are dictating. The recording is transcribed on the phone with a speech model you downloaded. The audio is not saved as a file. Voice is off in password and private fields.

**Clipboard.** While Lowkey is the selected keyboard, it can keep recent clips so you can paste them. Text longer than 20,000 characters is not kept in that history. Clips marked sensitive are not kept. History is hidden in password fields. A clip lasts for the time you set (the default is one hour after you copied it or last pasted it; you can choose 10 minutes, one day, or until the keyboard restarts). History does not outlast the app process. Copied images are cached in the app's private storage and removed when they leave the history or the app restarts.

**Diagnostics.** Crash reports and a voice-session log stay on the phone. A crash report can include the app version, Android version, device manufacturer and model, and a stack trace. The voice-session log stores a session number, time, a count of words, timing, and the names of the steps the session went through. It does not store the dictated sentence. Nothing on that screen is sent by the app. You can copy a crash report yourself if you want to include it in a message.

**AI keys.** If you use experimental AI, your OpenAI API key or server token is encrypted on the phone with a key that stays in Android's keystore. The app shows only the last four characters as a hint. The key is not part of a backup export. It is read when you send a request.

**Theme photo.** If you set a keyboard background, Lowkey keeps a smaller copy and drops the photo's location and other metadata. Choosing that photo uses Android's photo picker, which shares only the picture you pick.

**Backups Android would make.** Android backup of Lowkey is turned off, including cloud backup and device-to-device transfer of the app's data. A new phone or a reinstall starts empty unless you import a file you exported.

## What leaves your phone

Something does leave the phone. This section is the list. Op Studio does not receive the contents of these connections on a server of its own. The host at the other end sees a normal internet connection from your phone, including your IP address, and the address of the file or request. Op Studio does not get a copy of that log from the app.

### Speech models

When you download a speech model, the phone downloads the pinned files from Hugging Face (`huggingface.co`), over HTTPS. The models are OpenAI Whisper and Distil-Whisper exports published for sherpa-onnx. They are stored in the app's private storage. The request is for those files. It does not include what you have typed or said. Hugging Face, and the delivery network it uses, can see the connection.

### Keyboard languages

English (US) is in the app. When you add another language, the phone downloads that language's word list from the Android Open Source Project at `android.googlesource.com`, over HTTPS, at a fixed published version, and checks the file. The request is for the word list. It does not include your dictionary. The host can see the connection.

### Update check and update download

About once a day, and when you open the app if the last check is more than about six hours old, Lowkey can request a small text file from GitHub:

`https://github.com/opstudio-eu/lowkey/releases/latest/download/update.properties`

The request tells GitHub the app name and version (`Lowkey/` plus the version). It does not send what you type, your dictionary, or an advertising identifier. You can turn automatic checks off under Settings → About & updates. A check you start yourself uses the same request.

The update file downloads only if you tap Update. It comes from this app's GitHub releases, over HTTPS, and the app checks its size and checksum. Android asks you to allow the install. On newer Android versions, a later update of an app that installed the current version itself may ask less often. That is Android's rule.

In the source this draft was checked against, the check runs only when the installed package id is `com.rheerkens.androidkeyboard`. A build with another package id does not contact GitHub for updates until that condition is changed. A Google Play build may be updated by Play instead.

### Experimental AI, if you turn it on

This feature is off until you turn it on under Settings → Experimental. Lowkey does not send the field you were typing in. It sends only what is in the AI prompt when you tap the button, to the provider you chose:

- **OpenAI.** The phone calls `https://api.openai.com/v1` with your API key. OpenAI charges the account that owns the key.
- **A server you name.** The phone calls an HTTPS address you enter (an OpenAI-compatible API), with an optional access token. Release builds do not send the prompt over plain HTTP.

Creating an image sends the prompt and, if you added pictures, up to four pictures. Each picture is reduced so the long side is at most 1,536 pixels. "Improve" sends the prompt and how many pictures are attached, not the pictures themselves, together with a short instruction that asks the model to rewrite the prompt.

The key travels in the request header to that provider. Op Studio does not see the key, the prompt or the pictures.

If the provider answers with a link to the image rather than the image itself, the phone downloads that link over HTTPS. The API key is not sent with that download. The host of the link can see the connection.

Lowkey does not send the prompt to a separate moderation service. The provider may refuse the request under its own safety rules, and it may say so in its reply. That decision is the provider's, under the provider's terms.

The provider's terms and privacy policy apply to what it receives. Several likely providers are in the United States. Op Studio does not control them and does not sign that contract for you.

### What Op Studio does not add

The app does not include an account, an advertising identifier, or an analytics or crash-reporting service that sends data to Op Studio or to an analytics vendor. The speech library is built without ONNX Runtime's Java telemetry transport. Diagnostics stay on the phone until you copy them yourself.

There are no in-app purchases. Any fee for experimental AI is charged by the provider you chose, to the account that owns the key.

If you install Lowkey from Google Play, Google processes that download under Google's own terms. The app does not send your Play account to Op Studio.

## Photos

Recent screenshots on the keyboard are optional. The switch starts on, and the pictures appear only after you allow photo access. With access to all photos, Lowkey can list up to five screenshots from the last day that Android has filed in a Screenshots folder. With limited access, new screenshots stay hidden. You can turn the feature off, and you can revoke the permission in Android settings.

Those screenshots are read on the phone. They are not uploaded unless you attach one to an experimental AI prompt.

The photo picker (for a keyboard background, or for pictures you add to an AI prompt) shares only the pictures you choose. The background image does not need the photos permission.

If you turn on "Save to Gallery" for images the AI feature creates, those images are written to Pictures/Lowkey on the phone. They stay there until you delete them. They are not uploaded by that save.

## Permissions

Android will ask you for the dangerous ones. The others are declared so the app can do the named job.

| Permission | What it is for |
| --- | --- |
| Microphone | Dictation, only while you are dictating |
| Internet | Model and language downloads, the update check and download, and experimental AI if you use it |
| Network state | So a scheduled update check can wait for a connection |
| Notifications | Optional. Tells you an update is ready. Without it, the notice stays in the app |
| Photos (Android 13 and later), or storage on older versions | Optional. Recent screenshots. You can refuse |
| Install packages | Lets you install an update of Lowkey that you chose to download |

A foreground task runs only while an update you asked for is downloading, so the download can finish if you leave the app. It shows a progress notification.

Being the keyboard is a separate Android role. You turn it on in system settings. That role is why the app can see the field you are typing in and recent clipboard clips, as described above.

## Copies you make

Under Settings → Backup you can export saved words, speech rules and settings to a file you choose (`lowkey-backup.json`). The file is readable text. It can include the AI server address you saved. It does not include API keys, tokens, speech models, word lists or the theme photo. If you put that file in cloud storage, that service receives it. Keep it somewhere private.

Pasting into another app sends that clip to the other app, under that app's terms. Op Studio does not receive the paste.

## Children

Lowkey is a general-purpose keyboard. It is not directed at children. It does not ask your age, and it has no account to attach an age to.

The app still processes what the person holding the phone types and, if they use them, voice and experimental AI. A parent who lets a child use the phone should know that. The experimental AI feature sends the prompt to the provider the key belongs to.

## How long it stays, and how to delete it

Op Studio has no copy of your typing, voice, dictionary or photos to delete, because the app does not send those to Op Studio.

On the phone you can delete:

- a speech model, under Voice → Speech models
- a downloaded language and its word list, under Settings → Languages & layouts
- saved words and speech rules, under Dictionary
- learned predictions, with "Forget what Lowkey learned"
- clipboard history, from the keyboard
- crash reports and the voice-session log, under Settings → About & updates
- the API key or token, under Settings → Experimental
- the theme image, under the keyboard theme
- recent-screenshot access, with the switch in the app and the permission in Android settings

Uninstalling the app removes its private storage, including models, the dictionary, settings, keys and diagnostics. It does not remove Pictures/Lowkey, and it does not remove an export file you saved somewhere else. Those stay until you delete them.

If you email the support address, Op Studio receives that message and anything you attach, for example a crash report you pasted. The app does not send that report by itself. How long those emails are kept, and who can read them: [SUPPORT MAILBOX RETENTION AND ACCESS].

## Your rights

If the GDPR applies to processing Op Studio is responsible for, you can ask to access, correct or delete personal data, to restrict or object to processing, and to receive data you provided in a portable form where that right applies. You can complain to a supervisory authority. In Belgium that is the Gegevensbeschermingsautoriteit / Autorité de protection des données, <https://www.gegevensbeschermingsautoriteit.be>.

Because the app does not send your typing, voice or files to Op Studio, a request about those will often have this answer: there is no copy here. You can still write to the contact below. You will get an answer within the time the GDPR allows, usually one month. Deleting what is on the phone is what removes it from the phone. A message you email to Op Studio is different: Op Studio has that message, and you can ask about it.

The downloads and the optional AI calls go straight from your phone to those hosts. Their privacy notices describe what they do with connection logs and, for an AI provider, with the prompt you send. Op Studio does not operate those hosts and does not receive their logs through the app.

## Open source

The source code is public under the Apache License 2.0. The licence text is in the app under About & updates, and in the repository. Third-party components keep their own licences. The notices are in the app.

The Apache License does not grant the OpStudio name or logo. Forks and modified versions should remove the logo.

Publishing the source does not change the description above of what the installed app does.

## Changes to this policy

The current text, once published, will be at <https://opstudio.eu/lowkey/privacy>. If it changes, Op Studio will update that page and the effective date. A change that matters will also be noted in the app when the in-app notice is shown there.

The date on this draft is [EFFECTIVE DATE]. It replaces [PREVIOUS EFFECTIVE DATE, OR "NONE"].

## Contact

Op Studio  
[LEGAL NAME]  
[REGISTERED OFFICE ADDRESS]  
[support @ opstudio.eu]  
[TRADER PHONE NUMBER]
