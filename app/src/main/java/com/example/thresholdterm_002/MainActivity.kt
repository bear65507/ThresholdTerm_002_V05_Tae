package com.example.thresholdterm_002

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.thresholdterm_002.data.local.ProfileStore
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.databinding.ActivityMainBinding
import com.example.thresholdterm_002.ui.ActivityLauncher
import com.example.thresholdterm_002.ui.library.LibraryActivity
import com.example.thresholdterm_002.ui.stats.StatsActivity
import com.example.thresholdterm_002.ml.MediaPipeActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navHostView: View
    private val profileStore by lazy { ProfileStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navHostView = findViewById(R.id.nav_host_fragment_activity_main)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_notifications,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        configureProfileSetup()
        configureMainMenu()

        if (isProfileSaved()) {
            showMainApp()
        } else {
            showProfileSetup()
        }
    }

    private fun configureProfileSetup() {
        val sidoAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            ProfileStore.DISTRICTS_BY_SIDO.keys.toList()
        )
        binding.sidoInput.setAdapter(sidoAdapter)
        binding.sidoInput.setOnClickListener {
            binding.sidoInput.setText("", false)
            binding.sidoInput.showDropDown()
        }
        binding.sidoInput.setOnItemClickListener { _, _, position, _ ->
            val selectedSido = sidoAdapter.getItem(position).orEmpty()
            updateSigunguOptions(selectedSido)
        }

        val studentStatusAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.profile_student_status_options).toList()
        )
        binding.studentStatusInput.setAdapter(studentStatusAdapter)
        binding.studentStatusInput.setOnClickListener {
            binding.studentStatusInput.setText("", false)
            binding.studentStatusInput.showDropDown()
        }

        binding.saveProfileButton.setOnClickListener {
            saveProfileAndStart()
        }
    }

    private fun configureMainMenu() {
        binding.buttonMainTimer.setOnClickListener {
            startActivityForResult(
                ActivityLauncher.createIntent(this, MediaPipeActivity::class.java, "메인 허브"),
                REQUEST_TIMER
            )
        }
        binding.buttonMainStats.setOnClickListener {
            startActivity(ActivityLauncher.createIntent(this, StatsActivity::class.java, "메인 허브"))
        }
        binding.buttonMainLibrary.setOnClickListener {
            startActivity(ActivityLauncher.createIntent(this, LibraryActivity::class.java, "메인 허브"))
        }
        binding.buttonMainEditProfile.setOnClickListener {
            showProfileSetup()
        }
    }

    private fun updateSigunguOptions(sido: String) {
        val districts = ProfileStore.DISTRICTS_BY_SIDO[sido].orEmpty()
        val sigunguAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            districts
        )
        binding.sigunguInput.setAdapter(sigunguAdapter)
        binding.sigunguInput.setOnClickListener {
            binding.sigunguInput.setText("", false)
            binding.sigunguInput.showDropDown()
        }
        binding.sigunguInput.setText("", false)
        binding.eupMyeonDongInput.setText("")
        binding.sigunguInput.post {
            binding.sigunguInput.showDropDown()
        }
    }

    private fun saveProfileAndStart() {
        val sido = binding.sidoInput.text.toString().trim()
        val sigungu = binding.sigunguInput.text.toString().trim()
        val eupMyeonDong = binding.eupMyeonDongInput.text.toString().trim()
        val studentStatus = binding.studentStatusInput.text.toString().trim()

        if (sido.isBlank() || sigungu.isBlank() || eupMyeonDong.isBlank() || studentStatus.isBlank()) {
            Toast.makeText(this, R.string.profile_required_message, Toast.LENGTH_SHORT).show()
            return
        }

        profileStore.save(
            UserProfile(
                sido = sido,
                sigungu = sigungu,
                eupMyeonDong = eupMyeonDong,
                studentStatus = studentStatus
            )
        )

        showMainApp()
    }

    private fun isProfileSaved(): Boolean {
        return profileStore.isSaved()
    }

    private fun showProfileSetup() {
        supportActionBar?.setTitle(R.string.profile_setup_actionbar_title)
        binding.profileSetupPanel.visibility = View.VISIBLE
        binding.mainMenuPanel.visibility = View.GONE
        navHostView.visibility = View.GONE
        binding.navView.visibility = View.GONE
    }

    private fun showMainApp() {
        supportActionBar?.setTitle(R.string.app_name)
        binding.profileSetupPanel.visibility = View.GONE
        binding.mainMenuPanel.visibility = View.VISIBLE
        navHostView.visibility = View.GONE
        binding.navView.visibility = View.GONE
        updateMainProfileText()
    }

    private fun updateMainProfileText() {
        val profile = profileStore.getProfile()
        binding.textMainProfile.text = profile?.let {
            "현재 설정: ${it.regionLabel} / ${it.studentStatus}"
        } ?: "지역 및 학생 신분을 먼저 설정해주세요."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TIMER && resultCode == Activity.RESULT_OK) {
            val minutes = data?.getIntExtra(IntentExtras.EXTRA_COMPLETED_MINUTES, 0) ?: 0
            val focusScore = data?.getIntExtra(IntentExtras.EXTRA_COMPLETED_FOCUS_SCORE, 0) ?: 0
            binding.textMainLastResult.text =
                "최근 완료: ${minutes}분 집중 / 평균 집중도 ${focusScore}점\n공부 시간 집계 Activity에서 DB 기록을 확인할 수 있습니다."
        }
    }

    companion object {
        private const val REQUEST_TIMER = 1001
    }
}
