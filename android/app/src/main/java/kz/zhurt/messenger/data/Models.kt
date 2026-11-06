package kz.zhurt.messenger.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Строки таблиц Supabase (backend/supabase/migrations/20261106000001_init.sql)

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val gender: String? = null,
    val locale: String = "kk",
    val country: String = "KZ",
)

@Serializable
data class CircleRow(
    val id: String,
    @SerialName("family_space_id") val familySpaceId: String,
    val kind: String,              // shanyrak | oz | nagashy | kayin | toy
    val title: String,
    @SerialName("retention_days") val retentionDays: Int? = null,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
)

@Serializable
data class ConversationRow(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    val kind: String,              // group | pair | broadcast
    val title: String,
    @SerialName("broadcaster_id") val broadcasterId: String? = null,
    @SerialName("broadcast_attachments") val broadcastAttachments: Boolean = true,
)

@Serializable
data class CircleMemberRow(
    @SerialName("circle_id") val circleId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("can_write") val canWrite: Boolean = true,
    @SerialName("can_attach") val canAttach: Boolean = true,
    @SerialName("can_invite") val canInvite: Boolean = false,
    @SerialName("title_here") val titleHere: String? = null,
)

@Serializable
data class MessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String = "text",     // text | photo | video | file | voice | video_note
    val body: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class NewMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String,
    val body: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
)

@Serializable
data class AttachmentRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("uploader_id") val uploaderId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
)

@Serializable
data class InvitationPreview(
    @SerialName("circle_title") val circleTitle: String,
    @SerialName("circle_kind") val circleKind: String,
    @SerialName("inviter_name") val inviterName: String? = null,
    val role: String,
    @SerialName("title_here") val titleHere: String? = null,
    @SerialName("expires_at") val expiresAt: String,
)

/** Чат для списка: беседа + её круг */
data class ChatItem(
    val conversation: ConversationRow,
    val circle: CircleRow,
) {
    val canWriteHint: String
        get() = when {
            circle.closedAt != null -> "Той закрыт"
            conversation.kind == "broadcast" -> "рассылка"
            circle.retentionDays != null -> "${circle.retentionDays} дней"
            else -> "хранится"
        }
}

/** Состояние отправки (ТЗ 9): отправляется, на сервере, ошибка, ожидает сети */
enum class SendState { SENDING, SENT, FAILED, WAITING_NETWORK }

data class UiMessage(
    val row: MessageRow,
    val mine: Boolean,
    val state: SendState = SendState.SENT,
    val localUri: String? = null,
)
