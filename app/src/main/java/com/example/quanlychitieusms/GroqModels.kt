package com.example.quanlychitieusms
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
// Dữ liệu gửi đi
data class GroqRequest(
    val model: String = "llama-3.1-8b-instant",
    val messages: List<Message>
)

data class Message(val role: String, val content: String)

// Dữ liệu nhận về
data class GroqResponse(val choices: List<Choice>)
data class Choice(val message: Message)

// Interface để gọi API
interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun classifyTransaction(
        @Header("Authorization") apiKey: String,
        @Body request: GroqRequest
    ): GroqResponse
}