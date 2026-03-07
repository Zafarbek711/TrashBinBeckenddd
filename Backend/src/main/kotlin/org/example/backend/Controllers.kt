package org.example.backend

import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.telegram.telegrambots.bots.TelegramLongPollingBot

import jakarta.annotation.PostConstruct
import org.springframework.data.repository.findByIdOrNull
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.meta.api.objects.Update


@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val jwtUtil: JwtUtil,
    private val userRepository: UserRepository
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                request.username,
                request.password
            )
        )

        val dbUser = userRepository.findByUsername(request.username)
            ?: throw RuntimeException("User not found")

        val role = dbUser.role.name
        val token = jwtUtil.generateToken(dbUser.username, role)

        return LoginResponse(
            ok = true,
            token = token,
            username = dbUser.username,
            role = role
        )
    }

}

@RestController
@RequestMapping("/api/trashbins")
class TrashbinController(
    private val service: TrashBinService
) {

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping
    fun create(@Valid @RequestBody request: TrashBinCreateDto) =
        ResponseEntity.status(201).body(service.create(request))

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: TrashBinUpdateDto
    ) = ResponseEntity.ok(service.update(id, request))

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    fun getAll(pageable: Pageable) =
        ResponseEntity.ok(service.getAll(pageable))

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) =
        ResponseEntity.ok(service.getById(id))

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping("/{id}/fill")
    fun updateFillLevel(
        @PathVariable id: Long,
        @RequestParam fillLevel: Int
    ) = ResponseEntity.ok(service.updateFillLevel(id, fillLevel))

    @PreAuthorize("hasRole('DRIVER')")
    @PutMapping("/{id}/emptied")
    fun markEmptied(@PathVariable id: Long) =
        ResponseEntity.ok(service.markEmptied(id))
}

@RestController
@RequestMapping("/api/users")
class UserController(
    private val service: UserService,
    private val userRepository: UserRepository
) {

    // Faqat SUPER_ADMIN user yaratadi
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    fun create(@Valid @RequestBody request: UserCreateDto) =
        ResponseEntity.status(201).body(service.create(request))

    // SUPER_ADMIN hamma update qiladi
    // ADMIN faqat DRIVER ni update qila oladi
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UserUpdateDto
    ) = ResponseEntity.ok(service.update(id, request))

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping
    fun getAll(pageable: Pageable) =
        ResponseEntity.ok(service.getAll(pageable))

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) =
        ResponseEntity.ok(service.getById(id))

    @GetMapping("/admins")
    fun getAdmins(): List<UserResponseDto> {
        return service.getAdmins()
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
    @GetMapping("/drivers")
    fun getDrivers(): List<UserResponseDto> {
        return userRepository.findByRole(Role.DRIVER)
            .map { UserResponseDto.from(it) }
    }
}

@RestController
@RequestMapping("/api/ai")
class AiController(
    private val trashBinService: TrashBinService
) {

    @PostMapping("/trashbin-status")
    fun updateFromAi(@RequestBody request: AiTrashBinRequest)
            : ResponseEntity<TrashBinResponseDto> {

        return ResponseEntity.ok(trashBinService.updateFromAi(request))
    }
}

@Component
class MyTelegramBot(
    @Value("\${telegram.bot-token}")
    private val botToken: String,

    private val userRepository: UserRepository,
    private val trashBinRepository: TrashBinRepository,
    private val driverActionRepository: DriverActionRepository,
    private val telegramService: TelegramService

) : TelegramLongPollingBot() {

    override fun getBotToken(): String = botToken

    override fun getBotUsername(): String = "obodshahar_bot"

    override fun onUpdateReceived(update: Update) {

        // 🔹 QABUL QILDIM TUGMASI
        if (update.hasCallbackQuery()) {

            val data = update.callbackQuery.data
            val chatId = update.callbackQuery.message.chatId.toString()


            if (data.startsWith("ACK_")) {

                val callback = AnswerCallbackQuery()
                callback.callbackQueryId = update.callbackQuery.id
                execute(callback)

                val binId = data.removePrefix("ACK_").toLong()

                val bin = trashBinRepository.findByIdOrNull(binId)

                if (bin != null) {

                    // agar boshqa driver allaqachon olgan bo‘lsa
                    if (bin.acknowledged) {

                        val msg = SendMessage(
                            chatId,
                            "❌ Bu chiqindi boshqa haydovchi tomonidan qabul qilingan"
                        )

                        execute(msg)
                        return
                    }

                    // birinchi bosgan driver
                    bin.acknowledged = true
                    trashBinRepository.save(bin)

                    val user = userRepository.findByTelegramChatId(chatId)
                    if (user != null) {

                        driverActionRepository.save(
                            DriverAction(
                                driver = user,
                                trashBin = bin,
                                action = "ACCEPTED"
                            )
                        )
                    }

                    val msg = SendMessage(
                        chatId,
                        "🚚 Haydovchi faol — yo‘lga chiqdi"
                    )

                    execute(msg)
                }
            }
            if (data.startsWith("PROBLEM_")) {

                val binId = data.removePrefix("PROBLEM_").toLong()

                val bin = trashBinRepository.findByIdOrNull(binId)

                if (bin != null) {

                    // qayta ochamiz
                    bin.acknowledged = false
                    trashBinRepository.save(bin)

                    // driverga javob
                    val msg = SendMessage(
                        chatId,
                        "⚠️ Muammo qayd etildi.\nBoshqa haydovchilarga yuborilmoqda."
                    )

                    execute(msg)

                    // boshqa driverlarga qayta yuborish
                    bin.drivers.forEach { driver ->

                        val otherChatId = driver.telegramChatId

                        if (otherChatId != null && otherChatId != chatId) {

                            telegramService.sendFullBinNotification(
                                chatId = otherChatId,
                                binId = bin.id!!,
                                binName = bin.name,
                                fillLevel = bin.fillLevel,
                                lat = bin.latitude,
                                lon = bin.longitude
                            )
                        }
                    }
                }
            }
        }

        // 🔹 /start komandasi
        if (update.hasMessage() && update.message.hasText()) {

            val text = update.message.text
            val chatId = update.message.chatId.toString()
            val username = update.message.from.userName

            if (text == "/start" && username != null) {

                val user = userRepository.findByUsername(username)

                if (user != null) {
                    user.telegramChatId = chatId
                    userRepository.save(user)

                    val msg = SendMessage(
                        chatId,
                        "✅ Siz tizimga muvaffaqiyatli ulandingiz!"
                    )

                    execute(msg)

                } else {

                    val msg = SendMessage(
                        chatId,
                        "❌ Siz tizimda ro‘yxatdan o‘tmagansiz."
                    )

                    execute(msg)
                }
            }
        }
    }
}

@RestController
@RequestMapping("/api/statistics")
class StatisticsController(

    private val driverActionService: DriverActionService

) {

    @GetMapping("/driver/{driverId}")
    fun getDriverStatistics(
        @PathVariable driverId: Long
    ): List<DriverActionResponseDto> {

        return driverActionService.getDriverActions(driverId)

    }

    @GetMapping("/drivers")
    fun getAllDriverStatistics(): List<DriverActionResponseDto> {
        return driverActionService.getAllDriverActions()
    }
}