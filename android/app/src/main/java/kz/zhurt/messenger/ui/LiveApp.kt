package kz.zhurt.messenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import kz.zhurt.messenger.data.ChatItem
import kz.zhurt.messenger.data.CircleRow
import kz.zhurt.messenger.data.MessageRow
import kz.zhurt.messenger.data.ProfileRow
import kz.zhurt.messenger.data.ZhurtRepository

private val Gold = Color(0xFFE8C468)
private val Muted = Color(0xFFB7C7BC)
private val Panel = Color(0xFF13261C)
private val Them = Color(0xFF234433)
private val Me = Color(0xFF3F5D32)
private val Ink = Color(0xFF142016)
private val Warn = Color(0xFFE7B0A4)

private val FOLDS = listOf(
    "all" to "все", "shanyrak" to "шаңырақ", "oz" to "өз жұрт",
    "nagashy" to "нағашы", "kayin" to "қайын", "toy" to "той"
)

private fun kindLabel(kind: String) = FOLDS.firstOrNull { it.first == kind }?.second ?: kind

private sealed interface Screen {
    data object Loading : Screen
    data object Phone : Screen
    data class Code(val phone: String) : Screen
    data object Profile : Screen
    data object Onboarding : Screen
    data object Chats : Screen
    data class Chat(val item: ChatItem) : Screen
}

@Composable
fun LiveApp(repo: ZhurtRepository) {
    val session by repo.sessionStatus.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
    var place by remember { mutableStateOf(Place.KZ) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(session, refresh) {
        when (session) {
            is SessionStatus.Initializing -> screen = Screen.Loading
            is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure ->
                if (screen !is Screen.Code) screen = Screen.Phone
            is SessionStatus.Authenticated -> {
                if (screen is Screen.Chat) return@LaunchedEffect
                screen = runCatching {
                    repo.flushOutbox()
                    when {
                        repo.myProfile() == null -> Screen.Profile
                        repo.circles().isEmpty() -> Screen.Onboarding
                        else -> Screen.Chats
                    }
                }.getOrElse { error = it.message; Screen.Chats }
            }
        }
    }

    Ornament(place) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            error?.let {
                Text(it, color = Warn, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Panel).clickable { error = null }.padding(10.dp))
            }
            when (val s = screen) {
                Screen.Loading -> Centered { CircularProgressIndicator(color = Gold) }
                Screen.Phone -> PhoneScreen(repo, onSent = { screen = Screen.Code(it) }, onError = { error = it })
                is Screen.Code -> CodeScreen(repo, s.phone, onBack = { screen = Screen.Phone }, onError = { error = it })
                Screen.Profile -> ProfileScreen(repo, onDone = { refresh++ }, onError = { error = it })
                Screen.Onboarding -> OnboardingScreen(repo, onDone = { screen = Screen.Chats }, onError = { error = it })
                Screen.Chats -> ChatListScreen(repo, place, onPlace = { place = it }, onOpen = { screen = Screen.Chat(it) }, onError = { error = it })
                is Screen.Chat -> ChatScreen(repo, s.item, onBack = { screen = Screen.Chats }, onError = { error = it })
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Text(text, color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    if (sub != null) Text(sub, color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun GoldButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Вход ----------------
@Composable
private fun PhoneScreen(repo: ZhurtRepository, onSent: (String) -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("+7") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Title("Жұрт", "Өз адамдарыңның чаты. Войдите по номеру телефона.")
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Телефон") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
        Spacer(Modifier.height(12.dp))
        GoldButton(if (busy) "Отправляем…" else "Получить код", enabled = !busy && phone.count { it.isDigit() } >= 10) {
            busy = true
            scope.launch {
                runCatching { repo.sendCode(phone) }
                    .onSuccess { onSent(phone) }
                    .onFailure { onError("Код не отправлен: ${it.message}") }
                busy = false
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Номер видите только вы. Родственники видят имя.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun CodeScreen(repo: ZhurtRepository, phone: String, onBack: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Title("Код из SMS", "Отправили на ${ZhurtRepository.normalizePhone(phone)}")
        OutlinedTextField(code, { if (it.length <= 6) code = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
            label = { Text("6 цифр") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
        Spacer(Modifier.height(12.dp))
        GoldButton("Войти", enabled = !busy && code.length == 6) {
            busy = true
            scope.launch {
                runCatching { repo.verifyCode(phone, code) }.onFailure { onError("Неверный код: ${it.message}") }
                busy = false
            }
        }
        TextButton(onClick = onBack) { Text("Другой номер", color = Gold) }
    }
}

// ---------------- Профиль ----------------
@Composable
private fun ProfileScreen(repo: ZhurtRepository, onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    var locale by remember { mutableStateOf("kk") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Title("Таныстық", "Как вас называть в семье")
        OutlinedTextField(name, { name = it.take(64) }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        Text("Пол нужен, чтобы правильно подобрать обращение (қарындас или сіңлі)", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("ер адам", gender == "m") { gender = "m" }
            Chip("әйел адам", gender == "f") { gender = "f" }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("қазақша", locale == "kk") { locale = "kk" }
            Chip("русский", locale == "ru") { locale = "ru" }
        }
        Spacer(Modifier.height(16.dp))
        GoldButton("Сақтау", enabled = name.isNotBlank()) {
            scope.launch { runCatching { repo.saveProfile(name, gender, locale) }.onSuccess { onDone() }.onFailure { onError(it.message ?: "Ошибка") } }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(
        containerColor = if (on) Gold else Color.Transparent, contentColor = if (on) Ink else Muted)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Первый запуск: создать Шаңырақ или войти по приглашению ----------------
@Composable
private fun OnboardingScreen(repo: ZhurtRepository, onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var family by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Title("Шаңырақ", "Создайте домашний круг или войдите по приглашению")
        OutlinedTextField(family, { family = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Фамилия семьи, например «Кенжебаевтар»") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        GoldButton("Создать Шаңырақ", enabled = family.isNotBlank()) {
            scope.launch { runCatching { repo.createFamilySpace(family.trim()) }.onSuccess { onDone() }.onFailure { onError(it.message ?: "Ошибка") } }
        }
        Spacer(Modifier.height(28.dp))
        Text("Есть код приглашения?", color = Gold, fontWeight = FontWeight.Bold)
        OutlinedTextField(code, { code = it.uppercase().take(12); preview = null }, Modifier.fillMaxWidth(), label = { Text("Код") }, singleLine = true)
        preview?.let { Text(it, color = Muted, modifier = Modifier.padding(vertical = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        GoldButton(if (preview == null) "Проверить" else "Принять", enabled = code.length >= 6) {
            scope.launch {
                if (preview == null) {
                    val p = runCatching { repo.previewInvitation(code) }.getOrNull()
                    if (p == null) onError("Код недействителен или истёк")
                    else preview = "${p.inviterName ?: "Кто-то"} приглашает вас в круг «${p.circleTitle}» (${kindLabel(p.circleKind)})" +
                        (p.titleHere?.let { " как $it" } ?: "") + ". В круге увидят ваше имя, номер скрыт."
                } else {
                    runCatching { repo.acceptInvitation(code) }.onSuccess { onDone() }.onFailure { onError(it.message ?: "Ошибка") }
                }
            }
        }
    }
}

// ---------------- Список чатов ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListScreen(repo: ZhurtRepository, place: Place, onPlace: (Place) -> Unit, onOpen: (ChatItem) -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val chats = remember { mutableStateListOf<ChatItem>() }
    var fold by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var joinCode by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        runCatching { repo.chats() }.onSuccess { chats.clear(); chats.addAll(it) }.onFailure { onError(it.message ?: "Нет сети") }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Жұрт", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showNew = true }) { Text("+ круг", color = Gold, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Place.entries.forEach { p -> Chip(p.label, place == p) { onPlace(p) } }
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Имя, круг, той") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FOLDS.forEach { (id, label) -> Chip(label, fold == id) { fold = id } }
        }
        val shown = chats.filter { (fold == "all" || it.circle.kind == fold) && "${it.conversation.title} ${it.circle.title}".contains(query, true) }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.conversation.id }) { chat ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(chat) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Letter(chat.conversation.title.take(1).uppercase())
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(chat.conversation.title, fontWeight = FontWeight.Bold)
                        Text("${kindLabel(chat.circle.kind)} · ${chat.circle.title} · ${chat.canWriteHint}", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showNew) {
        ModalBottomSheet(onDismissRequest = { showNew = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Новый круг", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                val space = chats.firstOrNull()?.circle?.familySpaceId
                listOf("oz" to "Өз жұрт · братья, сёстры", "nagashy" to "Нағашы · сторона матери",
                    "kayin" to "Қайын · сторона супруга, 30 дней", "toy" to "Той · временный, закроется через 3 дня после события").forEach { (kind, label) ->
                    Text(label, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable(enabled = space != null) {
                        scope.launch {
                            runCatching { repo.createCircle(space!!, kind, kindLabel(kind).replaceFirstChar { it.uppercase() }) }
                                .onSuccess { showNew = false; reload++ }
                                .onFailure { onError(it.message ?: "Ошибка") }
                        }
                    }.padding(vertical = 12.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Войти в круг по коду", color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(joinCode, { joinCode = it.uppercase().take(12) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Код") })
                    TextButton(onClick = {
                        scope.launch { runCatching { repo.acceptInvitation(joinCode) }.onSuccess { showNew = false; joinCode = ""; reload++ }.onFailure { onError("Код недействителен или истёк") } }
                    }) { Text("Войти", color = Gold) }
                }
                Text("Круги не видят друг друга: участник нағашы не увидит переписку өз жұрт.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Letter(letter: String) {
    Column(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF345744)),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(letter, color = Gold, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Чат ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(repo: ZhurtRepository, chat: ChatItem, onBack: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<MessageRow>() }
    val pendingIds = remember { mutableStateMapOf<String, Boolean>() } // id → ошибка?
    val names = remember { mutableStateMapOf<String, ProfileRow>() }
    var draft by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<MessageRow?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var canPost by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val me = repo.myId
    val conv = chat.conversation
    val closed = chat.circle.closedAt != null
    val broadcastLocked = conv.kind == "broadcast" && conv.broadcasterId != me

    fun upsert(m: MessageRow) {
        val i = messages.indexOfFirst { it.id == m.id }
        if (i >= 0) messages[i] = m else messages.add(m)
        pendingIds.remove(m.id)
    }

    LaunchedEffect(conv.id) {
        runCatching {
            val h = repo.history(conv.id)
            messages.clear(); messages.addAll(h)
            val members = repo.members(chat.circle.id)
            names.putAll(repo.profiles(members.map { it.userId }))
            val mine = members.firstOrNull { it.userId == me }
            canPost = mine != null && mine.canWrite && mine.role != "observer"
        }.onFailure { onError(it.message ?: "Нет сети") }
        runCatching { repo.live(conv.id).collect { upsert(it) } }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    fun sendText() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val editId = editing
        if (editId != null) {
            editing = null
            draft = ""
            scope.launch { runCatching { repo.editMessage(editId, text) }.onFailure { onError(it.message ?: "Ошибка") } }
            return
        }
        val m = repo.newTextMessage(conv.id, text)
        draft = ""
        messages.add(MessageRow(m.id, m.conversationId, m.senderId, m.kind, m.body))
        pendingIds[m.id] = false
        scope.launch { runCatching { repo.send(m) }.onFailure { pendingIds[m.id] = true } }
    }

    fun sendFile(uri: Uri, kind: String) {
        scope.launch {
            runCatching {
                val (bytes, mime, ext) = readUri(context, uri)
                if (bytes.size > 100 * 1024 * 1024) error("Файл больше 100 МБ")
                repo.sendAttachment(conv.id, kind, bytes, mime, ext)
            }.onFailure { onError("Не отправлено: ${it.message}") }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> sendFile(u, "photo") } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> sendFile(u, "video") } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> sendFile(u, "file") } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                scope.launch { runCatching { repo.leaveLive(conv.id) } }
                onBack()
            }) { Text("←", color = Gold, fontSize = 20.sp) }
            Letter(conv.title.take(1).uppercase())
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(conv.title, fontWeight = FontWeight.Bold)
                Text("${kindLabel(chat.circle.kind)} · ${chat.canWriteHint}", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { showInfo = true }) { Text("круг", color = Gold) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages, key = { it.id }) { m ->
                val author = names[m.senderId]?.displayName ?: "…"
                Bubble(repo, m, mine = m.senderId == me, author = author, pending = pendingIds[m.id]) { held = m }
            }
        }
        when {
            closed -> Text("Той закрыт. Писать сюда больше нельзя.", color = Warn, modifier = Modifier.padding(12.dp))
            broadcastLocked -> Text("Рассылка: пишет только ата, вложения тоже. Обсуждение — в общем чате.", color = Warn, modifier = Modifier.padding(12.dp))
            !canPost -> Text("У вас права только на чтение.", color = Warn, modifier = Modifier.padding(12.dp))
            else -> Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showAttach = true }) { Text("+", color = Gold, fontSize = 24.sp) }
                OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text(if (editing != null) "Изменение" else "Хабар") })
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::sendText, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text(if (editing != null) "сақтау" else "жіберу") }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(onDismissRequest = { showAttach = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Приложить", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                SheetRow("Фото") { showAttach = false; pickPhoto.launch("image/*") }
                SheetRow("Видео") { showAttach = false; pickVideo.launch("video/*") }
                SheetRow("Документ") { showAttach = false; pickFile.launch(arrayOf("*/*")) }
                Text("Файл остаётся в этом чате. Переслать его в другой чат или в Избранное нельзя.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showInfo) {
        InfoSheet(repo, chat, names, onClose = { showInfo = false }, onError = onError)
    }

    held?.let { m ->
        ModalBottomSheet(onDismissRequest = { held = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp)) {
                Text("Пересылка запрещена правилами этого чата", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Warn)
                Text("Сообщения и вложения «${conv.title}» не переносятся в другие круги и в Избранное.", color = Muted)
                if (m.senderId == me && m.deletedAt == null) {
                    Spacer(Modifier.height(12.dp))
                    if (m.kind == "text") SheetRow("Изменить") { draft = m.body.orEmpty(); editing = m.id; held = null }
                    SheetRow("Удалить у всех") {
                        held = null
                        scope.launch { runCatching { repo.deleteMessage(m.id) }.onFailure { onError(it.message ?: "Ошибка") } }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(repo: ZhurtRepository, chat: ChatItem, names: Map<String, ProfileRow>, onClose: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<kz.zhurt.messenger.data.CircleMemberRow>>(emptyList()) }
    var invite by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    LaunchedEffect(chat.circle.id) { runCatching { members = repo.members(chat.circle.id) } }
    val circle: CircleRow = chat.circle
    val iCanInvite = members.firstOrNull { it.userId == repo.myId }?.let { it.canInvite || it.role in listOf("creator", "admin") } == true

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(16.dp)) {
            Text(circle.title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(kindLabel(circle.kind) + (circle.retentionDays?.let { " · история $it дней" } ?: ""), color = Muted)
            Spacer(Modifier.height(8.dp))
            members.forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(names[it.userId]?.displayName ?: "…")
                    Text(it.titleHere ?: it.role, color = Gold, fontWeight = FontWeight.Bold)
                }
            }
            if (iCanInvite) {
                Spacer(Modifier.height(12.dp))
                Text("Пригласить", color = Gold, fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Кто он(а) здесь: ана, аға, құда…") })
                if (invite == null) {
                    SheetRow("Создать код (72 часа, 1 раз)") {
                        scope.launch {
                            runCatching { repo.createInvitation(circle.id, title.ifBlank { null }, null, guest = circle.kind == "toy") }
                                .onSuccess { invite = it }.onFailure { onError(it.message ?: "Ошибка") }
                        }
                    }
                } else {
                    Text(invite!!, color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    SheetRow("Скопировать код") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Жұрт", "Код приглашения в Жұрт: ${invite!!}"))
                    }
                    Text("Отправьте код лично. Код действует 72 часа и только для одного человека.", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Медиа привязано к этому чату. В другой круг его не переслать.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetRow(title: String, onClick: () -> Unit) {
    Text(title, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), fontWeight = FontWeight.Bold, color = Gold)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(repo: ZhurtRepository, m: MessageRow, mine: Boolean, author: String, pending: Boolean?, onHold: () -> Unit) {
    var url by remember(m.attachmentId) { mutableStateOf<String?>(null) }
    var fileInfo by remember(m.attachmentId) { mutableStateOf<String?>(null) }
    LaunchedEffect(m.attachmentId) {
        val id = m.attachmentId ?: return@LaunchedEffect
        runCatching {
            val att = repo.attachment(id) ?: return@runCatching
            fileInfo = "${att.mimeType} · ${att.sizeBytes / 1024} КБ"
            if (m.kind == "photo") url = repo.attachmentUrl(att)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) Me else Them)
                .combinedClickable(onClick = {}, onLongClick = onHold)
                .padding(10.dp)
        ) {
            if (!mine) Text(author, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            when {
                m.deletedAt != null -> Text("Сообщение удалено", color = Muted)
                m.kind == "photo" -> {
                    if (url != null) AsyncImage(model = url, contentDescription = "фото",
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    else Text("Фото · загружается…", color = Muted)
                }
                m.kind == "video" -> Text("Видео · ${fileInfo ?: "…"}")
                m.kind == "file" -> Text("Документ · ${fileInfo ?: "…"}")
                m.kind == "voice" -> Text("Голосовое")
                m.kind == "video_note" -> Text("Видеозаметка")
                else -> Text(m.body.orEmpty())
            }
            if (m.body != null && m.kind != "text") Text(m.body)
            val status = when (pending) { null -> if (m.editedAt != null) "изменено" else null; false -> "отправляется…"; true -> "ожидает сети" }
            if (status != null) Text(status, color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

private fun readUri(context: Context, uri: Uri): Triple<ByteArray, String, String> {
    val cr = context.contentResolver
    val mime = cr.getType(uri) ?: "application/octet-stream"
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать файл")
    return Triple(bytes, mime, ext)
}
