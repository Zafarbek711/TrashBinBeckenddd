package org.example.backend

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface TrashBinRepository : JpaRepository<TrashBin, Long> {

    fun findByDriversContaining(driver: User, pageable: Pageable): Page<TrashBin>

    fun findByCameraId(cameraId: String): TrashBin?
}

interface UserRepository : JpaRepository<User, Long>{
    fun findByUsername(username: String): User?
    fun findByRole(role: Role): List<User>
    fun findByTelegramChatId(chatId: String): User?
}


interface DriverActionRepository : JpaRepository<DriverAction, Long> {

    fun findByDriverId(driverId: Long): List<DriverAction>

}