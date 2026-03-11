package org.example.backend

import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface TrashBinRepository : JpaRepository<TrashBin, Long> {

    fun findByDriversContaining(driver: User, pageable: Pageable): Page<TrashBin>

    fun findByCameraId(cameraId: String): TrashBin?
}

interface UserRepository : JpaRepository<User, Long>{
    fun findByUsername(username: String): User?
    fun findByRole(role: Role): List<User>
    fun findByTelegramChatId(chatId: String): User?
    @Query("""
    SELECT u FROM User u 
    JOIN TrashBin t 
    JOIN t.drivers d 
    WHERE t.id = :binId
""")
    fun findDriversByTrashBinId(binId: Long): List<User>
}


interface DriverActionRepository : JpaRepository<DriverAction, Long> {

    fun findByDriverId(driverId: Long): List<DriverAction>
    @Modifying
    @Transactional
    @Query("DELETE FROM DriverAction d WHERE d.driver.id = :driverId")
    fun deleteByDriverId(driverId: Long)

}
