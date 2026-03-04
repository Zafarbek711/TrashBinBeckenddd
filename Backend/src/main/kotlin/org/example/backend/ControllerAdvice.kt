package org.example.backend

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<BaseMessage> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            BaseMessage(
                code = 404,
                message = e.message
            )
        )
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: RuntimeException): ResponseEntity<BaseMessage> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            BaseMessage(
                code = 400,
                message = e.message
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleOtherErrors(e: Exception): ResponseEntity<BaseMessage> {
        e.printStackTrace()
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            BaseMessage(
                code = 500,
                message = "Serverda kutilmagan xatolik"
            )
        )
    }
}