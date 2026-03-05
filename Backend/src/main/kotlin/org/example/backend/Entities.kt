package org.example.backend

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdDate: LocalDateTime? = null,
    @LastModifiedDate
    @Column(nullable = false)
    var updatedDate: LocalDateTime? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedDate = LocalDateTime.now()
    }
}

@Entity
@Table(
    name = "trash_bins",
    indexes = [
        Index(name = "idx_trashbin_status", columnList = "status"),
        Index(
            name = "idx_trashbin_loc" +
                    "" +
                    "ation", columnList = "latitude, longitude"
        )
    ]
)
class TrashBin(
    @Column(nullable = false) var name: String,
    @Column(nullable = false) var latitude: Double,
    @Column(nullable = false) var longitude: Double,
    @field:Min(0)
    @field:Max(100)
    @Column(nullable = false) var fillLevel: Int = 0,

    @Column(nullable = false, unique = true)
    var cameraId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BinStatus = BinStatus.EMPTY,

    var lastEmptiedAt: LocalDateTime? = null,

    @Column
    var imageUrl: String? = null,
    @ManyToOne
    @JoinColumn(name = "driver_id")
    var driver: User? = null,

    @Column
    var fullDetectedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var acknowledged: Boolean = false,

    @Column(nullable = false)
    var escalatedToAdmin: Boolean = false,

    @Column(nullable = false)
    var escalatedToSuperAdmin: Boolean = false


) : BaseEntity() {
    // Smart logic — fillLevel o‘zgarsa status avtomatik yangilanadi
    fun updateFillLevel(newLevel: Int) {
        this.fillLevel = newLevel
        this.status = calculateStatus(newLevel)
    }

    private fun calculateStatus(level: Int): BinStatus {
        return when {
            level >= 90 -> BinStatus.FULL
            else -> BinStatus.EMPTY
        }
    }

    fun markAsEmptied() {
        this.fillLevel = 0
        this.status = BinStatus.EMPTY
        this.lastEmptiedAt = LocalDateTime.now()
    }
}

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false) var fullname: String,

    @Column(nullable = false, unique = true) var username: String,

    @Column(nullable = false) var password: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    @Column(nullable = false)
    var email: String? = null,

    @Column(name = "telegram_chat_id")
    var telegramChatId: String? = null

) : BaseEntity()




