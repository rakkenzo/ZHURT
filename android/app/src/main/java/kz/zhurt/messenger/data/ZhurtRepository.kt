package kz.zhurt.messenger.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Единая точка работы с сервером. Все права и изоляция кругов проверяются на сервере (RLS),
 * клиент только отображает то, что ему выдали.
 */
class ZhurtRepository(private val supabase: SupabaseClient, private val outbox: Outbox) {

    private val channels = java.util.concurrent.ConcurrentHashMap<String, io.github.jan.supabase.realtime.RealtimeChannel>()

    // ---------- Вход по номеру телефона (OTP) ----------
    val sessionStatus: StateFlow<SessionStatus> get() = supabase.auth.sessionStatus
    val myId: String? get() = supabase.auth.currentUserOrNull()?.id

    suspend fun sendCode(phone: String) {
        supabase.auth.signInWith(OTP) { this.phone = normalizePhone(phone) }
    }

    suspend fun verifyCode(phone: String, code: String) {
        supabase.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = normalizePhone(phone), token = code.trim())
    }

    suspend fun signOut() = supabase.auth.signOut()

    // ---------- Профиль ----------
    suspend fun myProfile(): ProfileRow? {
        val id = myId ?: return null
        return supabase.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull<ProfileRow>()
    }

    suspend fun saveProfile(name: String, gender: String?, locale: String) {
        val id = requireNotNull(myId)
        supabase.from("profiles").upsert(ProfileRow(id = id, displayName = name.trim(), gender = gender, locale = locale))
    }

    suspend fun profiles(ids: Collection<String>): Map<String, ProfileRow> {
        if (ids.isEmpty()) return emptyMap()
        return supabase.from("profiles").select { filter { isIn("id", ids.toList()) } }
            .decodeList<ProfileRow>().associateBy { it.id }
    }

    // ---------- Семья, круги, приглашения ----------
    suspend fun createFamilySpace(name: String): String =
        rpcString("create_family_space", buildJsonObject { put("p_name", name) })

    suspend fun createCircle(spaceId: String, kind: String, title: String, eventAtIso: String? = null): String =
        rpcString("create_circle", buildJsonObject {
            put("p_space", spaceId); put("p_kind", kind); put("p_title", title)
            if (eventAtIso != null) put("p_event_at", eventAtIso)
        })

    suspend fun createInvitation(circleId: String, titleHere: String?, inviteeName: String?, guest: Boolean): String =
        rpcString("create_invitation", buildJsonObject {
            put("p_circle", circleId)
            put("p_role", if (guest) "toy_guest" else "member")
            if (titleHere != null) put("p_title_here", titleHere)
            if (inviteeName != null) put("p_invitee_name", inviteeName)
        })

    suspend fun previewInvitation(code: String): InvitationPreview? =
        supabase.postgrest.rpc("preview_invitation", buildJsonObject { put("p_code", code.trim()) })
            .decodeList<InvitationPreview>().firstOrNull()

    suspend fun acceptInvitation(code: String): String =
        rpcString("accept_invitation", buildJsonObject { put("p_code", code.trim()) })

    suspend fun circles(): List<CircleRow> =
        supabase.from("circles").select { order("created_at", Order.ASCENDING) }.decodeList()

    suspend fun members(circleId: String): List<CircleMemberRow> =
        supabase.from("circle_members").select { filter { eq("circle_id", circleId) } }.decodeList()

    suspend fun chats(): List<ChatItem> {
        val circles = circles().associateBy { it.id }
        return supabase.from("conversations").select { order("created_at", Order.ASCENDING) }
            .decodeList<ConversationRow>()
            .mapNotNull { cv -> circles[cv.circleId]?.let { ChatItem(cv, it) } }
    }

    // ---------- Сообщения ----------
    suspend fun history(conversationId: String, limit: Long = 200): List<MessageRow> =
        supabase.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<MessageRow>().reversed()

    /** Новые и изменённые сообщения чата в реальном времени. */
    suspend fun live(conversationId: String): Flow<MessageRow> {
        channels.remove(conversationId)?.let { runCatching { supabase.realtime.removeChannel(it) } }
        val ch = supabase.channel("conv-$conversationId")
        channels[conversationId] = ch
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.mapNotNull { action ->
            when (action) {
                is PostgresAction.Insert -> action.decodeRecord<MessageRow>()
                is PostgresAction.Update -> action.decodeRecord<MessageRow>()
                else -> null
            }
        }
        ch.subscribe()
        return flow
    }

    suspend fun leaveLive(conversationId: String) {
        channels.remove(conversationId)?.let { supabase.realtime.removeChannel(it) }
    }

    /**
     * Отправка текста. id создаёт клиент: если сеть пропала и мы отправим повторно,
     * сервер не создаст дубликат (on conflict do nothing).
     */
    fun newTextMessage(conversationId: String, text: String, replyTo: String? = null): NewMessage =
        NewMessage(UUID.randomUUID().toString(), conversationId, requireNotNull(myId), "text", text.trim(), null, replyTo)

    suspend fun send(msg: NewMessage) {
        outbox.put(msg)
        supabase.from("messages").upsert(msg) {
            onConflict = "id"
            ignoreDuplicates = true
        }
        outbox.remove(msg.id)
    }

    /** Повторить всё, что не ушло (вызывать при появлении сети и при старте). */
    suspend fun flushOutbox(): Int {
        var sent = 0
        for (m in outbox.all()) {
            runCatching { send(m) }.onSuccess { sent++ }
        }
        return sent
    }

    fun pending(conversationId: String): List<NewMessage> = outbox.all().filter { it.conversationId == conversationId }

    suspend fun editMessage(id: String, body: String) {
        supabase.from("messages").update({ set("body", body.trim()) }) { filter { eq("id", id) } }
    }

    suspend fun deleteMessage(id: String) {
        supabase.from("messages").update({ set("deleted_at", "now()") }) { filter { eq("id", id) } }
    }

    // ---------- Вложения (принадлежат одному чату) ----------
    /**
     * Загружает файл в приватное хранилище по пути "<conversation_id>/<uuid>.<ext>"
     * и создаёт сообщение. Переслать вложение в другой чат сервер не даст.
     */
    suspend fun sendAttachment(conversationId: String, kind: String, bytes: ByteArray, mime: String, ext: String, caption: String? = null): NewMessage {
        val me = requireNotNull(myId)
        val attachmentId = UUID.randomUUID().toString()
        val path = "$conversationId/$attachmentId.${ext.ifBlank { "bin" }}"
        supabase.storage.from(BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        supabase.from("attachments").insert(
            AttachmentRow(attachmentId, conversationId, me, path, mime, bytes.size.toLong())
        )
        val msg = NewMessage(UUID.randomUUID().toString(), conversationId, me, kind, caption, attachmentId)
        send(msg)
        return msg
    }

    suspend fun attachment(id: String): AttachmentRow? =
        supabase.from("attachments").select { filter { eq("id", id) } }.decodeSingleOrNull()

    /** Короткоживущая ссылка: доступ проверяется сервером при каждой выдаче. */
    suspend fun attachmentUrl(att: AttachmentRow): String =
        supabase.storage.from(BUCKET).createSignedUrl(att.storagePath, 5.minutes)

    // ---------- Kinship Engine (Edge Function) ----------
    suspend fun kinshipTerm(spaceId: String, personId: String): JsonObject {
        val res = supabase.functions.invoke("kinship", buildJsonObject {
            put("space_id", spaceId); put("target_person_id", personId)
        })
        return Json.parseToJsonElement(res.bodyAsText()) as JsonObject
    }

    private suspend fun rpcString(fn: String, args: JsonObject): String {
        val text = supabase.postgrest.rpc(fn, args).data
        return Json.parseToJsonElement(text).jsonPrimitive.content
    }

    companion object {
        const val BUCKET = "attachments"

        /** +7 777 123 45 67 / 87771234567 → +77771234567 (Казахстан по умолчанию) */
        fun normalizePhone(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return when {
                digits.length == 11 && digits.startsWith("8") -> "+7" + digits.drop(1)
                digits.length == 10 -> "+7$digits"
                else -> "+$digits"
            }
        }
    }
}
