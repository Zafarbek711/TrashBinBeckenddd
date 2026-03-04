package org.example.backend

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class DataInitializer {

    @Bean
    fun initData(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder
    ): CommandLineRunner {

        return CommandLineRunner {

            if (userRepository.count() == 0L) {

                val superAdmin = User(
                    fullname = "Super Admin",
                    username = "superadmin",
                    password = passwordEncoder.encode("12345"),
                    role = Role.SUPER_ADMIN,
                    email = "superadmin@gmail.com"
                )

                val admin = User(
                    fullname = "Admin",
                    username = "admin",
                    password = passwordEncoder.encode("12345"),
                    role = Role.ADMIN,
                    email ="user@gmail.com"
                )

                val driver = User(
                    fullname = "Driver",
                    username = "driver",
                    password = passwordEncoder.encode("12345"),
                    role = Role.DRIVER,
                    email="driver@gmail.com"
                )

                userRepository.saveAll(listOf(superAdmin, admin, driver))

                println("🔥 Default users created")
            }
        }
    }
}