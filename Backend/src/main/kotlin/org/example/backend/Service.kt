package org.example.backend

import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime

class NotFoundException(message: String) : RuntimeException(message)

interface TrashBinService {
    fun create(request: TrashBinCreateDto): TrashBinResponseDto
    fun update(id: Long, request: TrashBinUpdateDto): TrashBinResponseDto
    fun updateFillLevel(id: Long, fillLevel: Int): TrashBinResponseDto


    fun markEmptied(id: Long): TrashBinResponseDto
    fun getAll(pageable: Pageable): Page<TrashBinResponseDto>
    fun getById(id: Long): TrashBinResponseDto
    fun delete(id: Long)
    fun updateFromAi(request: AiTrashBinRequest): TrashBinResponseDto
}

interface UserService {
    fun create(request: UserCreateDto): UserResponseDto
    fun update(id: Long, request: UserUpdateDto): UserResponseDto
    fun getAll(pageable: Pageable): Page<UserResponseDto>
    fun getById(id: Long): UserResponseDto
    fun delete(id: Long)

    fun getDrivers(): List<UserResponseDto>
    fun getAdmins(): List<UserResponseDto>
}


interface DriverActionService {

    fun getDriverActions(driverId: Long): List<DriverActionResponseDto>

    fun getAllDriverActions(): List<DriverActionResponseDto>

}

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findAll()
            .find { it.username == username }
            ?: throw UsernameNotFoundException("User not found")

        return org.springframework.security.core.userdetails.User(
            user.username,
            user.password,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )
    }
}

//telegram
@Service
class TelegramService(
    @Value("\${telegram.bot-token}")
    private val botToken: String
) {

    private val baseUrl = "https://api.telegram.org/bot$botToken"

    fun sendFullBinNotification(
        chatId: String,
        binId: Long,
        binName: String,
        fillLevel: Int,
        lat: Double,
        lon: Double
    ) {
        try {
            val url = URL("$baseUrl/sendMessage")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val message = """
🚨 <b>CHIQINDI IDISHI TO‘LDI</b>

━━━━━━━━━━━━━━━━
🗑 <b>$binName</b>
📊 To‘lish darajasi: <b>$fillLevel%</b>
━━━━━━━━━━━━━━━━

⏳ 30 minut ichida tasdiqlang.
""".trimIndent()

            val json = """
{
  "chat_id": "$chatId",
  "text": "$message",
  "parse_mode": "HTML",
  "reply_markup": {
    "inline_keyboard": [
      [
        {
          "text": "✅ Qabul qildim",
          "callback_data": "ACK_$binId"
        }
      ],
      [
        {
          "text": "❌ Menda muammo bor",
          "callback_data": "PROBLEM_$binId"
        }
      ],
      [
        {
          "text": "📍 Xarita ochish",
          "url": "https://maps.google.com/?q=$lat,$lon"
        }
      ]
    ]
  }
}
""".trimIndent()

            connection.outputStream.use {
                it.write(json.toByteArray())
            }

            println("TELEGRAM RESPONSE CODE: ${connection.responseCode}")

        } catch (e: Exception) {
            println("❌ TELEGRAM ERROR")
            e.printStackTrace()
        }
    }

    fun sendMessage(chatId: String, text: String) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = "$baseUrl/sendMessage?chat_id=$chatId&text=$encodedText"
            URL(url).readText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

//emailga
@Service
class EmailService(
    private val mailSender: JavaMailSender,
) {
    fun sendEmail(to: String, subject: String, text: String) {
        val message = SimpleMailMessage()
        message.setTo(to)
        message.subject = subject
        message.setText(text)
        mailSender.send(message)
    }
}

@Service
@Transactional
class UserServiceImpl(
    private val repository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val trashBinRepository: TrashBinRepository,
    private val driverActionRepository: DriverActionRepository
) : UserService {
    override fun create(request: UserCreateDto): UserResponseDto {
        val user = request.toUserEntity()
        user.password = passwordEncoder.encode(user.password)

        return UserResponseDto.from(repository.save(user))
    }


    override fun update(id: Long, request: UserUpdateDto): UserResponseDto {

        val user = repository.findByIdOrNull(id)
            ?: throw NotFoundException("User not found")

        val currentUser = SecurityContextHolder.getContext().authentication

        val currentRole = currentUser!!.authorities.first().authority

        // ADMIN adminni update qilolmaydi
        if (currentRole == "ROLE_ADMIN" && user.role != Role.DRIVER) {
            throw RuntimeException("Admin faqat DRIVER ni tahrirlay oladi")
        }

        request.fullname?.let { user.fullname = it }
        request.username?.let { user.username = it }
        request.password?.let { user.password = it }
        request.role?.let { user.role = it }
        request.email?.let { user.email = it }

        request.password?.let {
            user.password = passwordEncoder.encode(it)
        }

        return UserResponseDto.from(user)
    }

    override fun getAll(pageable: Pageable): Page<UserResponseDto> {
        return repository.findAll(pageable).map {
            UserResponseDto.from(it)
        }
    }

    override fun getById(id: Long): UserResponseDto {
        val user = repository.findByIdOrNull(id)
            ?: throw NotFoundException("User with id $id not found")
        return UserResponseDto.from(user)
    }

    override fun delete(id: Long) {

        val user = repository.findByIdOrNull(id)
            ?: throw NotFoundException("User with id $id not found")

        // trashbinlardan driverni olib tashlash
        trashBinRepository.findAll().forEach {
            it.drivers.remove(user)
        }

        // driver actionlarni o‘chirish
        driverActionRepository.deleteByDriverId(id)

        // userni o‘chirish
        repository.delete(user)
    }

    override fun getDrivers(): List<UserResponseDto> {
        return repository.findByRole(Role.DRIVER)
            .map { UserResponseDto.from(it) }
    }

    override fun getAdmins(): List<UserResponseDto> {
        return repository.findByRole(Role.ADMIN)
            .map { UserResponseDto.from(it) }
    }

}

@Service
@Transactional
class TrashBinServiceImpl(
    private val repository: TrashBinRepository,
    private val telegramService: TelegramService,
    private val userRepository: UserRepository,

    ) : TrashBinService {

    override fun create(request: TrashBinCreateDto): TrashBinResponseDto {

        val drivers = userRepository.findAllById(request.driverIds)

        val bin = TrashBin(
            name = request.name,
            cameraId = request.cameraId,
            latitude = request.latitude,
            longitude = request.longitude,
            fillLevel = request.fillLevel,
            drivers = drivers.toMutableList()
        )

        bin.updateFillLevel(request.fillLevel)

        return TrashBinResponseDto.from(repository.save(bin))
    }

    override fun update(id: Long, request: TrashBinUpdateDto): TrashBinResponseDto {

        val bin = repository.findByIdOrNull(id)
            ?: throw NotFoundException("Trashbin with id $id not found")

        request.name?.let { bin.name = it }
        request.latitude?.let { bin.latitude = it }
        request.longitude?.let { bin.longitude = it }

        // 🔥 DRIVERLARNI UPDATE QILISH
        request.driverIds?.let {

            val drivers = userRepository.findAllById(it)

            bin.drivers.clear()
            bin.drivers.addAll(drivers)
        }

        request.fillLevel?.let {
            val previousStatus = bin.status
            bin.updateFillLevel(it)
            checkAndNotifyIfFull(bin, previousStatus)
        }

        return TrashBinResponseDto.from(repository.save(bin))
    }

    override fun updateFillLevel(id: Long, fillLevel: Int): TrashBinResponseDto {

        val bin = repository.findByIdOrNull(id)
            ?: throw NotFoundException("TrashBin with id $id not found")

        val previousStatus = bin.status

        bin.updateFillLevel(fillLevel)

        checkAndNotifyIfFull(bin, previousStatus)

        return TrashBinResponseDto.from(bin)
    }

    override fun markEmptied(id: Long): TrashBinResponseDto {
        val bin = repository.findByIdOrNull(id)
            ?: throw NotFoundException("TrashBin with id $id not found")

        bin.markAsEmptied()

        return TrashBinResponseDto.from(bin)
    }

    override fun getById(id: Long): TrashBinResponseDto {
        val bin = repository.findByIdOrNull(id)
            ?: throw NotFoundException("TrashBin with id $id not found")

        return TrashBinResponseDto.from(bin)
    }

    override fun delete(id: Long) {
        val bin = repository.findByIdOrNull(id)
            ?: throw NotFoundException("TrashBin with id $id not found")

        repository.delete(bin)
    }

    override fun getAll(pageable: Pageable): Page<TrashBinResponseDto> {

        val auth = SecurityContextHolder.getContext().authentication
        val username = auth!!.name
        val role = auth.authorities.first().authority

        if (role == "ROLE_DRIVER") {

            val driver = userRepository.findByUsername(username)
                ?: throw RuntimeException("Driver not found")

            return repository.findByDriversContaining(driver, pageable)
                .map { TrashBinResponseDto.from(it) }
        }

        return repository.findAll(pageable)
            .map { TrashBinResponseDto.from(it) }
    }

    override fun updateFromAi(request: AiTrashBinRequest): TrashBinResponseDto {

        val bin = repository.findByCameraId(request.cameraId)
            ?: throw RuntimeException("Trash bin not found")

        val fillLevel = if (request.isFull) 95 else 10
        bin.updateFillLevel(fillLevel)

        // 🔥 AI yuborgan rasmni saqlaymiz
        bin.imageBase64 = request.imageBase64

        if (request.isFull) {

            bin.drivers.forEach { driver ->

                val chatId = driver.telegramChatId

                if (chatId != null) {

                    bin.fullDetectedAt = LocalDateTime.now()
                    bin.acknowledged = false
                    bin.escalatedToAdmin = false
                    bin.escalatedToSuperAdmin = false

                    telegramService.sendFullBinNotification(
                        chatId = chatId,
                        binId = bin.id!!,
                        binName = bin.name,
                        fillLevel = bin.fillLevel,
                        lat = bin.latitude,
                        lon = bin.longitude
                    )
                }
            }
        }

        // 🔥 DB ga saqlaymiz
        repository.save(bin)

        return TrashBinResponseDto.from(bin)
    }

    private fun checkAndNotifyIfFull(bin: TrashBin, previousStatus: BinStatus?) {

        println("PREVIOUS STATUS: $previousStatus")
        println("NEW STATUS: ${bin.status}")

        if (previousStatus == BinStatus.FULL) {
            println("❌ Old status FULL bo‘lgani uchun xabar yuborilmadi")
            return
        }

        if (bin.status != BinStatus.FULL) {
            println("❌ Hali FULL emas")
            return
        }

        bin.fullDetectedAt = LocalDateTime.now()
        bin.acknowledged = false
        bin.escalatedToAdmin = false
        bin.escalatedToSuperAdmin = false

        bin.drivers.forEach { driver ->

            val chatId = driver.telegramChatId ?: return@forEach

            telegramService.sendFullBinNotification(
                chatId = chatId,
                binId = bin.id!!,
                binName = bin.name,
                fillLevel = bin.fillLevel,
                lat = bin.latitude,
                lon = bin.longitude
            )
        }

        println("✅ DRIVERLARGA XABAR YUBORILDI")
    }
}

@Service
class EscalationService(
    private val trashBinRepository: TrashBinRepository,
    private val telegramService: TelegramService,
    private val userRepository: UserRepository
) {

    @Scheduled(fixedDelay = 60000) // har 1 minutda ishlaydi
    fun checkEscalations() {

        val now = LocalDateTime.now()

        val fullBins = trashBinRepository.findAll()
            .filter { it.status == BinStatus.FULL && !it.acknowledged }

        fullBins.forEach { bin ->

            val detectedTime = bin.fullDetectedAt ?: return@forEach

            val minutesPassed =
                java.time.Duration.between(detectedTime, now).toMinutes()

            // 🔥 30 MINUT — ADMIN
            if (minutesPassed >= 30 && !bin.escalatedToAdmin) {

                val admin = userRepository.findAll()
                    .firstOrNull { it.role == Role.ADMIN }

                admin?.telegramChatId?.let { chatId ->
                    telegramService.sendMessage(
                        chatId,
                        "⚠️ Driver javob bermadi! ${bin.name} hali ham FULL."
                    )
                }

                bin.escalatedToAdmin = true
                trashBinRepository.save(bin)

                println("🚨 ESCALATED TO ADMIN")
            }

            // 🔥 60 MINUT — SUPER ADMIN
            if (minutesPassed >= 60 && !bin.escalatedToSuperAdmin) {

                val superAdmin = userRepository.findAll()
                    .firstOrNull { it.role == Role.SUPER_ADMIN }

                superAdmin?.telegramChatId?.let { chatId ->
                    telegramService.sendMessage(
                        chatId,
                        "🚨 ADMIN ham javob bermadi! ${bin.name} 1 soatdan beri FULL."
                    )
                }

                bin.escalatedToSuperAdmin = true
                trashBinRepository.save(bin)

                println("🔥 ESCALATED TO SUPER ADMIN")
            }
        }
    }
}

@Service
class DriverActionServiceImpl(
    private val driverActionRepository: DriverActionRepository
) : DriverActionService {

    override fun getDriverActions(driverId: Long): List<DriverActionResponseDto> {

        return driverActionRepository.findByDriverId(driverId)
            .map { DriverActionResponseDto.from(it) }

    }

    override fun getAllDriverActions(): List<DriverActionResponseDto> {

        return driverActionRepository.findAll()
            .map { DriverActionResponseDto.from(it) }

    }
}
