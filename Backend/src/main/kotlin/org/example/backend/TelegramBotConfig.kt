package org.example.backend

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.meta.generics.LongPollingBot

@Configuration
class TelegramBotConfig(
    private val bot: LongPollingBot
) {

    @PostConstruct
    fun init() {
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        botsApi.registerBot(bot)
    }
}