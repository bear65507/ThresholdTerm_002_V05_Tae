package com.example.thresholdterm_002.ui.exam

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.thresholdterm_002.IntentExtras
import com.example.thresholdterm_002.data.remote.StudyResourceDownloadManager
import com.example.thresholdterm_002.databinding.ActivityExamDownloadBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ExamDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExamDownloadBinding
    private val downloadManager by lazy { StudyResourceDownloadManager(this) }
    private val loadedArchiveIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "기출문제 다운로드"

        bindProfileText()
        bindDownloadButtons()
    }

    private fun bindProfileText() {
        val region = listOf(
            intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")
        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        val sourceScreen = intent.getStringExtra(IntentExtras.EXTRA_SOURCE_SCREEN).orEmpty()

        binding.textExamDownloadProfile.text =
            "전달받은 정보: $region / $studentStatus / 출발 화면: $sourceScreen"
    }

    private fun bindDownloadButtons() {
        binding.buttonLoadOldSuneungCatalog.setOnClickListener {
            loadArchiveCatalog(
                source = OLD_SUNEUNG_SOURCE,
                button = binding.buttonLoadOldSuneungCatalog,
                container = binding.oldSuneungButtonContainer
            )
        }

        binding.buttonLoadRecentSuneungCatalog.setOnClickListener {
            loadArchiveCatalog(
                source = RECENT_SUNEUNG_SOURCE,
                button = binding.buttonLoadRecentSuneungCatalog,
                container = binding.recentSuneungButtonContainer
            )
        }

        binding.buttonLoadMockCatalog.setOnClickListener {
            loadArchiveCatalog(
                source = MOCK_SOURCE,
                button = binding.buttonLoadMockCatalog,
                container = binding.mockExamButtonContainer
            )
        }
    }

    private fun loadArchiveCatalog(
        source: ArchiveSource,
        button: Button,
        container: LinearLayout
    ) {
        if (source.boardId in loadedArchiveIds) {
            binding.textExamDownloadStatus.text = "${source.label} 버튼 목록은 이미 불러왔습니다."
            return
        }

        button.isEnabled = false
        binding.textExamDownloadStatus.text = "${source.label} 목록을 불러오는 중입니다..."
        container.removeAllViews()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fetchArchiveFiles(source)
                }
            }

            result.onSuccess { files ->
                loadedArchiveIds += source.boardId
                renderExamButtons(source.label, files, container)
                binding.textExamDownloadStatus.text =
                    "${source.label} 다운로드 버튼 ${files.size}개를 불러왔습니다.\n버튼을 누르면 DownloadManager로 바로 저장됩니다."
            }.onFailure {
                button.isEnabled = true
                binding.textExamDownloadStatus.text =
                    "${source.label} 목록을 불러오지 못했습니다. 네트워크 연결을 확인해주세요."
                Toast.makeText(this@ExamDownloadActivity, "목록 불러오기 실패", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderExamButtons(
        sourceLabel: String,
        files: List<ExamDownloadFile>,
        container: LinearLayout
    ) {
        container.removeAllViews()
        addSectionHeader(sourceLabel, container)
        files.forEach { file ->
            addDownloadButton(file, container)
        }
    }

    private fun addSectionHeader(text: String, container: LinearLayout) {
        val header = TextView(this).apply {
            this.text = text
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }
        container.addView(header)
    }

    private fun addDownloadButton(file: ExamDownloadFile, container: LinearLayout) {
        val button = Button(this).apply {
            text = file.buttonLabel
            isAllCaps = false
            setOnClickListener {
                enqueueExamDownload(file.title) {
                    downloadManager.enqueueStudyResource(
                        title = file.safeFileName,
                        url = file.downloadUrl,
                        mimeType = file.mimeType
                    )
                }
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8
        }
        container.addView(button, params)
    }

    private fun enqueueExamDownload(label: String, downloadAction: () -> Long) {
        try {
            downloadAction()
            binding.textExamDownloadStatus.text =
                "$label 다운로드를 시작했습니다.\n다운로드 알림 또는 다운로드 폴더에서 파일을 확인할 수 있습니다."
            Toast.makeText(this, "$label 다운로드를 시작했습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            binding.textExamDownloadStatus.text =
                "$label 다운로드를 시작할 수 없습니다. 네트워크 연결을 확인해주세요."
            Toast.makeText(this, "$label 다운로드를 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchArchiveFiles(source: ArchiveSource): List<ExamDownloadFile> {
        val firstPage = fetchText(source.pageUrl(1))
        val maxPage = extractMaxPage(firstPage).coerceAtLeast(1)
        val files = mutableListOf<ExamDownloadFile>()
        files += parseDownloadFiles(source, firstPage)

        for (page in 2..maxPage) {
            files += parseDownloadFiles(source, fetchText(source.pageUrl(page)))
        }

        return files.distinctBy { it.fileSeq }
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun extractMaxPage(html: String): Int {
        return Regex("page=([0-9]+)")
            .findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()
            ?.coerceAtMost(MAX_PAGES_PER_ARCHIVE)
            ?: 1
    }

    private fun parseDownloadFiles(source: ArchiveSource, html: String): List<ExamDownloadFile> {
        val rows = Regex("<tr[\\s\\S]*?</tr>", RegexOption.IGNORE_CASE).findAll(html)
        val files = mutableListOf<ExamDownloadFile>()

        rows.forEach { rowMatch ->
            val row = rowMatch.value
            val year = extractYear(row)
            val subject = extractSubject(row)
            val downloadMatches = FILE_DOWN_REGEX.findAll(row)

            downloadMatches.forEach { match ->
                val fileSeq = match.groupValues[1]
                val title = cleanHtml(match.groupValues[2])
                files += ExamDownloadFile(
                    sourceLabel = source.label,
                    year = year,
                    subject = subject,
                    title = title,
                    fileSeq = fileSeq
                )
            }
        }

        return files
    }

    private fun extractYear(row: String): String {
        return Regex("<td[^>]*>\\s*(19[0-9]{2}|20[0-9]{2})\\s*</td>", RegexOption.IGNORE_CASE)
            .find(row)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun extractSubject(row: String): String {
        return SUBJECTS.firstOrNull { subject ->
            Regex("<td[^>]*>\\s*${Regex.escape(subject)}\\s*</td>", RegexOption.IGNORE_CASE).containsMatchIn(row)
        }.orEmpty()
    }

    private fun cleanHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private data class ArchiveSource(
        val label: String,
        val boardId: String
    ) {
        fun pageUrl(page: Int): String {
            return "https://www.suneung.re.kr/boardCnts/list.do?type=default&page=$page&m=0403&boardID=$boardId&s=suneung"
        }
    }

    private data class ExamDownloadFile(
        val sourceLabel: String,
        val year: String,
        val subject: String,
        val title: String,
        val fileSeq: String
    ) {
        val downloadUrl: String
            get() = "https://www.suneung.re.kr/boardCnts/fileDown.do?fileSeq=$fileSeq"

        val safeFileName: String
            get() = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val mimeType: String
            get() = when (safeFileName.substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "hwp" -> "application/x-hwp"
                "exe" -> "application/octet-stream"
                else -> "application/octet-stream"
            }

        val buttonLabel: String
            get() = listOf(year, subject, title).filter { it.isNotBlank() }.joinToString(" / ")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 KICEExamDownloader/1.0"
        private const val MAX_PAGES_PER_ARCHIVE = 60

        private val FILE_DOWN_REGEX = Regex(
            "fn_fileDown\\('([^']+)'\\)[^>]*title='([^']+)'",
            RegexOption.IGNORE_CASE
        )

        private val SUBJECTS = listOf(
            "국어",
            "수학",
            "영어",
            "한국사",
            "사회탐구",
            "과학탐구",
            "직업탐구",
            "제2외국어/한문",
            "언어",
            "수리",
            "외국어"
        )

        private val OLD_SUNEUNG_SOURCE = ArchiveSource("1994~2004학년도 수능 기출", "1500235")
        private val RECENT_SUNEUNG_SOURCE = ArchiveSource("2005학년도 이후 수능 기출", "1500234")
        private val MOCK_SOURCE = ArchiveSource("수능 모의평가 기출", "1500236")
    }
}
