package com.rheerkens.androidkeyboard.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class HttpsArtifactFetcherTest {

    @Test
    fun cancellingAStalledDownloadReturnsPromptly() = runBlocking {
        // Accepts the connection and never answers, so the TLS handshake read blocks.
        val server = ServerSocket(0)
        val accepted = mutableListOf<Socket>()
        thread(isDaemon = true) { runCatching { while (true) accepted += server.accept() } }
        val artifact = ModelArtifact(
            "stall.bin", "https://127.0.0.1:${server.localPort}/stall.bin", 10, "0".repeat(64),
        )
        try {
            val started = System.nanoTime()
            val job = launch(Dispatchers.IO) {
                runCatching { HttpsArtifactFetcher().fetch(artifact, ByteArrayOutputStream()) {} }
            }
            delay(500)
            job.cancel()
            withTimeout(5_000) { job.join() }
            val seconds = (System.nanoTime() - started) / 1e9
            assertTrue("cancel took $seconds s", seconds < 5)
        } finally {
            accepted.forEach { runCatching { it.close() } }
            server.close()
        }
    }
}
