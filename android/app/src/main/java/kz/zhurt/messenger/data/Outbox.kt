package kz.zhurt.messenger.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Очередь неотправленных сообщений (ТЗ 9). Хранится в закрытой папке приложения,
 * переживает перезапуск. Повторная отправка безопасна: message_id стабилен.
 */
class Outbox(private val file: File?) {
    private val items = ConcurrentHashMap<String, NewMessage>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { json.decodeFromString(ListSerializer(NewMessage.serializer()), f.readText()) }
                .getOrNull()?.forEach { items[it.id] = it }
        }
    }

    fun put(m: NewMessage) { items[m.id] = m; save() }
    fun remove(id: String) { if (items.remove(id) != null) save() }
    fun all(): List<NewMessage> = items.values.toList()

    @Synchronized
    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(NewMessage.serializer()), items.values.toList()))
            tmp.renameTo(f)
        }
    }
}
