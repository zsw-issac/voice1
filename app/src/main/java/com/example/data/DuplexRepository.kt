package com.example.data

import kotlinx.coroutines.flow.Flow

class DuplexRepository(private val dao: ConversationDao) {

    val allConversations: Flow<List<ConversationEntity>> = dao.getAllConversations()

    suspend fun createConversation(title: String): Long {
        val conv = ConversationEntity(
            title = title,
            createdAt = System.currentTimeMillis()
        )
        return dao.insertConversation(conv)
    }

    suspend fun updateConversation(conv: ConversationEntity) {
        dao.updateConversation(conv)
    }

    suspend fun deleteConversation(id: Long) {
        dao.deleteMessagesForConversation(id)
        dao.deleteConversation(id)
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>> {
        return dao.getMessagesForConversation(conversationId)
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        wasInterrupted: Boolean = false,
        latencyMs: Long = 0
    ): Long {
        val msg = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            wasInterrupted = wasInterrupted,
            latencyMs = latencyMs
        )
        val id = dao.insertMessage(msg)
        val conv = dao.getConversationById(conversationId)
        if (conv != null) {
            dao.updateConversation(conv.copy(messageCount = conv.messageCount + 1))
        }
        return id
    }

    suspend fun clearAll() {
        dao.clearAllMessages()
        dao.clearAllConversations()
    }
}
