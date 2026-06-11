package com.example.thresholdterm_002.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.thresholdterm_002.R
import com.example.thresholdterm_002.data.local.ProfileStore
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val profileStore by lazy { ProfileStore(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        configureInputs()
        fillSavedProfile()
        binding.buttonSaveSettings.setOnClickListener {
            saveProfile()
        }
        return binding.root
    }

    private fun configureInputs() {
        val sidoAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            ProfileStore.DISTRICTS_BY_SIDO.keys.toList()
        )
        binding.settingsSidoInput.setAdapter(sidoAdapter)
        binding.settingsSidoInput.setOnClickListener {
            binding.settingsSidoInput.setText("", false)
            binding.settingsSidoInput.showDropDown()
        }
        binding.settingsSidoInput.setOnItemClickListener { _, _, position, _ ->
            val selectedSido = sidoAdapter.getItem(position).orEmpty()
            updateSigunguOptions(selectedSido, clearCurrentValue = true)
        }

        val studentStatusAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.profile_student_status_options).toList()
        )
        binding.settingsStudentStatusInput.setAdapter(studentStatusAdapter)
        binding.settingsStudentStatusInput.setOnClickListener {
            binding.settingsStudentStatusInput.setText("", false)
            binding.settingsStudentStatusInput.showDropDown()
        }
    }

    private fun fillSavedProfile() {
        val profile = profileStore.getProfile() ?: return
        binding.settingsSidoInput.setText(profile.sido, false)
        updateSigunguOptions(profile.sido, clearCurrentValue = false)
        binding.settingsSigunguInput.setText(profile.sigungu, false)
        binding.settingsEupMyeonDongInput.setText(profile.eupMyeonDong)
        binding.settingsStudentStatusInput.setText(profile.studentStatus, false)
        binding.textCurrentProfile.text = "현재 설정: ${profile.regionLabel} / ${profile.studentStatus}"
    }

    private fun updateSigunguOptions(sido: String, clearCurrentValue: Boolean) {
        val sigunguAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            ProfileStore.DISTRICTS_BY_SIDO[sido].orEmpty()
        )
        binding.settingsSigunguInput.setAdapter(sigunguAdapter)
        binding.settingsSigunguInput.setOnClickListener {
            binding.settingsSigunguInput.setText("", false)
            binding.settingsSigunguInput.showDropDown()
        }
        if (clearCurrentValue) {
            binding.settingsSigunguInput.setText("", false)
            binding.settingsEupMyeonDongInput.setText("")
        }
    }

    private fun saveProfile() {
        val profile = UserProfile(
            sido = binding.settingsSidoInput.text.toString().trim(),
            sigungu = binding.settingsSigunguInput.text.toString().trim(),
            eupMyeonDong = binding.settingsEupMyeonDongInput.text.toString().trim(),
            studentStatus = binding.settingsStudentStatusInput.text.toString().trim()
        )

        if (
            profile.sido.isBlank() ||
            profile.sigungu.isBlank() ||
            profile.eupMyeonDong.isBlank() ||
            profile.studentStatus.isBlank()
        ) {
            Toast.makeText(requireContext(), R.string.profile_required_message, Toast.LENGTH_SHORT).show()
            return
        }

        profileStore.save(profile)
        binding.textCurrentProfile.text = "현재 설정: ${profile.regionLabel} / ${profile.studentStatus}"
        Toast.makeText(requireContext(), "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.settingsSidoInput.dismissDropDown()
        binding.settingsSigunguInput.dismissDropDown()
        binding.settingsStudentStatusInput.dismissDropDown()
        binding.settingsSidoInput.setAdapter(null)
        binding.settingsSigunguInput.setAdapter(null)
        binding.settingsStudentStatusInput.setAdapter(null)
        super.onDestroyView()
        _binding = null
    }
}
