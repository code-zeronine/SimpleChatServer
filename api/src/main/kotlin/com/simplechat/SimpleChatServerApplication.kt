package com.simplechat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.simplechat"])
class SimpleChatServerApplication

fun main(args: Array<String>) {

    runApplication<SimpleChatServerApplication>(*args)
}