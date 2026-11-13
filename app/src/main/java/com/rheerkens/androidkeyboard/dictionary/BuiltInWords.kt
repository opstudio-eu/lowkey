package com.rheerkens.androidkeyboard.dictionary

/** Small built-in word lists for the suggester, roughly in frequency order. */
object BuiltInWords {
    val ENGLISH: List<String> = """
        the to and of a in is it you that for this on with what why how when can are be
        not do does have i my me we they at from or as but if so all will there their
        about which should would could just like make get use add run fix show explain
        refactor test tests error errors build function class value variable method
        return state screen code kotlin java android compose coroutine coroutines scope
        suspend launch flow stateflow view model repository injected inject dependency
        gradle wrapper task lint install fail fails failed failing storage insufficient
        cancel cancelled cancellation keyboard voice speech model models dictionary
        settings activity fragment service intent permission layout lifecycle android
        emulator device debug release commit branch merge rebase review request change
        changes update delete create remove rename move file files folder directory
        string list map set array null nullable type interface object data sealed enum
        private public internal override abstract open val var fun import package
        thread main background memory leak crash exception stack trace log logs
        query database table column index api endpoint json parse network response
        please thanks hello yes no maybe also then than into only each every same
        different first last next previous new old good better best problem issue
        works working without within between after before while until because
        example explain explanation implement implementation simple simpler unit
        integration performance optimize allocation garbage collection
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()

    /**
     * Developer and chat vocabulary missing from the 2014 AOSP word lists. Merged into every
     * lexicon as known words so autocorrect leaves them alone (`kotlin` must not become `Joplin`).
     */
    val TECH: List<String> = """
        lol lmao omg ok okay yeah yea yep nope btw tbh imo imho thx pls plz gonna wanna gotta
        idk ikr ngl smh fyi asap brb ttyl lmk irl dms tho ofc afaik iirc tbf wyd hbu wbu cuz
        convo omw
        api apis json yaml toml xml html css scss async await sync repo repos github gitlab
        git commit commits refactor refactoring refactored kotlin java javascript typescript
        python rust golang gradle android ios macos linux ubuntu debian windows coroutine
        coroutines viewmodel viewmodels composable composables compose jetpack lambda lambdas
        enum enums struct structs boolean booleans bool int ints nullable npm yarn pnpm node
        nodejs deno docker dockerfile kubernetes localhost backend frontend fullstack
        middleware endpoint endpoints auth oauth jwt webhook webhooks cli ui ux ide sdk sdks
        apk apks adb emulator emulators debugger debugging stacktrace regex regexes linter
        linting config configs env dev prod staging deploy deploys deployment rollback hotfix
        changelog readme markdown todo todos dedupe codebase monorepo microservice
        microservices serverless cron cache caching cached mutex threadpool runtime compiler
        transpile bundler webpack vite tailwind postgres postgresql mysql sqlite redis mongodb
        graphql grpc http https url urls uri uris websocket websockets utf ascii unicode emoji
        emojis screenshot screenshots tooltip tooltips dropdown navbar sidebar modal popup
        onboarding signup login logout username wifi bluetooth tailscale ssh sudo chmod bash
        zsh stdout stderr stdin utils util helpers params param args kwargs init getter setter
        getters setters inline workflow workflows pipeline pipelines diff diffs rebase rebasing
        squash upstream downstream formatter prettier eslint ktlint detekt pytest junit mockk
        mockito gemini claude gpt llm llms openai anthropic prompt prompts chatbot dataset
        datasets preprocessing inference finetune finetuning embedding embeddings tokenizer
        transformer transformers quantized quantization onnx tensorflow pytorch numpy pandas
        jupyter notebook notebooks moonshine whisper nemotron parakeet sherpa futo samsung
        pixel playstore firebase supabase vercel netlify cloudflare nginx apache tmux vim
        neovim vscode intellij xcode figma jira slack discord whatsapp youtube
        autocomplete autocompletes autocompleted autocompleting autocompletion
        autocorrect autocorrects autocorrected autocorrecting autocorrection autocorrections
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()

    val DUTCH: List<String> = """
        de het een en van ik je jij is dat die in te niet op met voor zijn er maar om
        ook als dan wat wel nog bij uit naar kan heb heeft hebben wordt worden was waren
        mijn jouw ons onze hun deze dit door over tot geen meer zo nu al hier daar waar
        waarom hoe wanneer welke goed graag dank bedankt hallo ja nee misschien morgen
        vandaag gisteren werk code bestand functie fout fouten test testen bouwen
        toetsenbord spraak model instellingen woordenboek uitleggen waarom doet
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
}
