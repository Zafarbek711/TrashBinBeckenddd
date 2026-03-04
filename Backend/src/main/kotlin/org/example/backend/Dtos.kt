package org.example.backend

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BaseMessage(
    val code: Int?,
    val message: String?,
)

data class TrashBinCreateDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val fillLevel: Int,
    val cameraId: String,
    val driverId: Long   // 🔥 QO‘SHILDI
)


data class TrashBinUpdateDto(
    var name: String?,
    var latitude: Double?,
    var longitude: Double?,
    @field:Min(0) @field:Max(100) val fillLevel: Int?,
)

data class TrashBinResponseDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val fillLevel: Int,
    val status: BinStatus,
    val imageUrl: String?
) {
    companion object {
        fun from(entity: TrashBin) = TrashBinResponseDto(
            id = entity.id!!,
            name = entity.name,
            latitude = entity.latitude,
            longitude = entity.longitude,
            fillLevel = entity.fillLevel,
            status = entity.status,
            imageUrl = entity.imageUrl
        )
    }
}


data class UserCreateDto(
    @field:NotBlank
    val fullname: String,
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
    val role: Role,
    val email: String,
) {
    fun toUserEntity(): User {
        return User(fullname, username, password, role, email)
    }
}

data class AiTrashBinRequest(

    @JsonProperty("camera_id")
    val cameraId: String,

    val status: BinStatus,

    @JsonProperty("is_full")
    val isFull: Boolean,

    val confidence: Double?,

    @JsonProperty("det_conf")
    val detConf: Double?,

    val ts: String?,

    val imageBase64: String?
)

data class UserUpdateDto(
    val fullname: String?,
    val username: String?,
    val password: String?,
    val role: Role?,
    val email: String?,
)

data class UserResponseDto(
    val id: Long,
    val fullname: String,
    val username: String,
    val role: Role,
    val email: String?,
) {
    companion object {
        fun from(entity: User) = UserResponseDto(
            id = entity.id!!,
            fullname = entity.fullname,
            username = entity.username,
            role = entity.role,
            email = entity.email,
        )
    }
}

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String
)