package org.example.backend

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface TrashBinRepository : JpaRepository<TrashBin, Long> {
    fun findByDriver(driver: User, pageable: Pageable): Page<TrashBin>
    fun findByCameraId(cameraId: String): TrashBin?
}

interface UserRepository : JpaRepository<User, Long>{
    fun findByUsername(username: String): User?
    fun findByRole(role: Role): List<User>
}
