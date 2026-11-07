package kz.zhurt.messenger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

private val Gold = Color(0xFFE8C468)
private val Muted = Color(0xFFB7C7BC)
private val Panel = Color(0xFF13261C)
private val Them = Color(0xFF234433)
private val Me = Color(0xFF3F5D32)

data class Member(val name: String, val role: String)
enum class Kind { TEXT, PHOTO, VIDEO, FILE, NOTE, VOICE }
data class Msg(
    val id: String,
    val from: String,
    val text: String,
    val mine: Boolean,
    val kind: Kind = Kind.TEXT,
    val uri: String? = null,
    val boundChat: String
)
data class Chat(
    val id: String,
    val fold: String,
    val title: String,
    val who: String,
    val letter: String,
    val status: String,
    val keep: String,
    val canWrite: Boolean,
    val members: List<Member>,
    val messages: MutableList<Msg>,
    var preview: String
)

private val folds = listOf(
    "all" to "все", "shan" to "шаңырақ", "oz" to "өз жұрт", "nag" to "нағашы",
    "kay" to "қайын", "toy" to "той", "saved" to "избранное"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoApp() {
    val context = LocalContext.current
    val chats = remember {
        mutableStateListOf(
            Chat("home", "shan", "Үй", "ана, әке, әйел", "Ү", "в сети", "хранится", true,
                listOf(Member("Айгүл", "ана"), Member("Нұрлан", "әке"), Member("Дина", "әйел")),
                mutableListOf(Msg("m1", "Айгүл · ана", "Нан ыстық. Фото дастархана можно ответом.", false, boundChat = "home")), "Нан ыстық"),
            Chat("pair", "shan", "Дина", "әйел", "Д", "была недавно", "хранится", true,
                listOf(Member("Дина", "әйел")),
                mutableListOf(Msg("m2", "Дина · әйел", "Құдаларға тізім файлом жібереміз бе?", false, boundChat = "pair")), "Тізім"),
            Chat("agin", "oz", "Ағайын", "аға, жеңге, іні, келін", "А", "5 человек", "хранится", true,
                listOf(Member("Ерлан", "аға"), Member("Дина", "жеңге"), Member("Бек", "іні"), Member("Самал", "келін")),
                mutableListOf(Msg("m3", "Ерлан · аға", "Жеңге шай қойды.", false, boundChat = "agin")), "Шай дайын"),
            Chat("ata", "oz", "Ата · рассылка", "только ата пишет", "С", "рассылка", "хранится", false,
                listOf(Member("Сейіт", "ата")),
                mutableListOf(Msg("m4", "Сейіт · ата", "Жексенбіде ауыл. Вложения тут тоже только от ата.", false, boundChat = "ata")), "Жексенбіде ауыл"),
            Chat("naga", "nag", "Серік", "нағашы аға", "С", "нағашы", "хранится", true,
                listOf(Member("Серік", "нағашы аға")),
                mutableListOf(Msg("m5", "Серік · нағашы аға", "Жиен, сенбіде кел.", false, boundChat = "naga")), "Жиен, кел"),
            Chat("ene", "kay", "Роза", "ене", "Р", "қайын · 30 дней", "30 дней", true,
                listOf(Member("Роза", "ене")),
                mutableListOf(Msg("m6", "Роза · ене", "Тойға смета құжатымен.", false, boundChat = "ene")), "Смета"),
            Chat("toy", "toy", "Құдалар", "той · закроется после", "Қ", "до тоя", "до тоя", true,
                listOf(Member("Бекзат", "құда")),
                mutableListOf(Msg("m7", "Бекзат · құда", "Келін түсер 18:00.", false, boundChat = "toy")), "18:00"),
            Chat("saved", "saved", "Избранное", "только вы", "★", "заметки", "хранится", true,
                listOf(Member("Вы", "мен")),
                mutableListOf(Msg("m8", "Вы", "Сюда можно кинуть билет и видеозаметку себе.", true, boundChat = "saved")), "Билет")
        )
    }
    var fold by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var showForward by remember { mutableStateOf<Msg?>(null) }
    var place by remember { mutableStateOf(Place.KZ) }
    var revision by remember { mutableStateOf(0) }
    val open = chats.firstOrNull { it.id == openId }

    fun push(kind: Kind, text: String, uri: Uri?) {
        val chat = chats.firstOrNull { it.id == openId } ?: return
        chat.messages.add(Msg("local-$revision", "Вы", text, true, kind, uri?.toString(), chat.id))
        chat.preview = text
        revision++
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) push(Kind.PHOTO, "Фото", uri)
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) push(Kind.VIDEO, "Видео", uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) push(Kind.FILE, "Документ", uri)
    }
    var noteUri by remember { mutableStateOf<Uri?>(null) }
    val captureNote = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        if (ok && noteUri != null) push(Kind.NOTE, "Видеозаметка", noteUri)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) noteUri?.let { captureNote.launch(it) }
    }

    if (open == null) {
        Ornament(place) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Жұрт", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Узор фона тихий и меняется со страной.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Place.entries.forEach { p ->
                    TextButton(onClick = { place = p }, colors = ButtonDefaults.textButtonColors(containerColor = if (place == p) Gold else Color.Transparent, contentColor = if (place == p) Color(0xFF142016) else Muted)) {
                        Text(p.label, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Имя, ана, той") })
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                folds.forEach { (id, label) ->
                    TextButton(onClick = { fold = id }, colors = ButtonDefaults.textButtonColors(containerColor = if (fold == id) Gold else Color.Transparent, contentColor = if (fold == id) Color(0xFF142016) else Muted)) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val shown = chats.filter { (fold == "all" || it.fold == fold) && "${it.title} ${it.who} ${it.preview}".contains(query, true) }
            LazyColumn {
                items(shown, key = { it.id }) { chat ->
                    Row(Modifier.fillMaxWidth().clickable { openId = chat.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BoxLetter(chat.letter)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(chat.title, fontWeight = FontWeight.Bold)
                            Text("${chat.who} · ${chat.preview}", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        }
    } else {
        Ornament(place) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openId = null }) { Text("←", color = Gold) }
                BoxLetter(open.letter)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(open.title, fontWeight = FontWeight.Bold)
                    Text("${open.status} · ${open.keep}", color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = { showInfo = true }) { Text("круг", color = Gold) }
            }
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(open.messages.size, key = { "$revision-$it" }) { i ->
                    MessageBubble(open.messages[i]) { showForward = open.messages[i] }
                }
            }
            if (open.canWrite) {
                var draft by remember(open.id) { mutableStateOf("") }
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showAttach = true }) { Text("+", color = Gold, fontSize = 24.sp) }
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Хабар") })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (draft.isBlank()) return@Button
                        push(Kind.TEXT, draft.trim(), null)
                        draft = ""
                    }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF142016))) { Text("жіберу") }
                }
            } else {
                Text("Рассылка: пишет только ата, вложения тоже.", color = Color(0xFFE7B0A4), modifier = Modifier.padding(12.dp))
            }
        }
        if (showInfo) {
            ModalBottomSheet(onDismissRequest = { showInfo = false }, sheetState = rememberModalBottomSheetState()) {
                Column(Modifier.padding(16.dp)) {
                    Text(open.title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    open.members.forEach {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.name); Text(it.role, color = Gold, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Медиа привязано к этому чату. В другой круг его не переслать.", color = Muted)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        if (showAttach) {
            ModalBottomSheet(onDismissRequest = { showAttach = false }, sheetState = rememberModalBottomSheetState()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Приложить", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    AttachRow("Фото") { showAttach = false; pickPhoto.launch("image/*") }
                    AttachRow("Видео") { showAttach = false; pickVideo.launch("video/*") }
                    AttachRow("Документ") { showAttach = false; pickFile.launch(arrayOf("*/*")) }
                    AttachRow("Видеозаметка") {
                        showAttach = false
                        val file = File(context.cacheDir, "note-${System.currentTimeMillis()}.mp4")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        noteUri = uri
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) captureNote.launch(uri) else askCamera.launch(Manifest.permission.CAMERA)
                    }
                    AttachRow("Голос") { showAttach = false; push(Kind.VOICE, "Голосовое · 0:04", null) }
                    Text("Файл остаётся в этом чате. Переслать фото, видео и документ в другой чат нельзя.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        val locked = showForward
        if (locked != null) {
            ModalBottomSheet(onDismissRequest = { showForward = null }, sheetState = rememberModalBottomSheetState()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Переслать нельзя", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFE7B0A4))
                    Text(
                        if (locked.kind == Kind.TEXT) "Текста в другой чат копия не уходит. Можно только сказать своими словами."
                        else "Это вложение принадлежит чату «${open.title}». В ағайын, құда, избранное и шаңырақ оно не переносится.",
                        color = Muted
                    )
                    chats.filter { it.id != open.id }.forEach { target ->
                        Text("${target.title} · закрыто", modifier = Modifier.padding(vertical = 8.dp), color = Gold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        }
    }
}

@Composable
private fun AttachRow(title: String, onClick: () -> Unit) {
    Text(title, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), fontWeight = FontWeight.Bold, color = Gold)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: Msg, onHold: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.fillMaxWidth(if (msg.kind == Kind.NOTE) 0.42f else 0.82f)
                .clip(if (msg.kind == Kind.NOTE) CircleShape else RoundedCornerShape(16.dp))
                .background(if (msg.mine) Me else Them)
                .combinedClickable(onClick = {}, onLongClick = onHold)
                .padding(if (msg.kind == Kind.NOTE) 0.dp else 10.dp)
        ) {
            if (msg.kind != Kind.NOTE) Text(msg.from, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            when (msg.kind) {
                Kind.PHOTO -> {
                    AsyncImage(model = msg.uri, contentDescription = "фото", modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Text("Фото")
                }
                Kind.VIDEO -> Text("Видео · нажмите, чтобы открыть позже\n${msg.uri.orEmpty()}")
                Kind.FILE -> Text("Документ\n${msg.uri.orEmpty().substringAfterLast('/')}")
                Kind.NOTE -> Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Text("видео\nзаметка", color = Gold, fontWeight = FontWeight.Bold)
                }
                Kind.VOICE -> Text("Голосовое · 0:04")
                Kind.TEXT -> Text(msg.text)
            }
        }
    }
}

@Composable
private fun BoxLetter(letter: String) {
    Column(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF345744)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { Text(letter, color = Gold, fontWeight = FontWeight.Bold) }
}
