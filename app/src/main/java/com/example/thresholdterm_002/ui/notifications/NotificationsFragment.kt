package com.example.thresholdterm_002.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.thresholdterm_002.data.local.ProfileStore
import com.example.thresholdterm_002.data.remote.GoogleMapNavigator
import com.example.thresholdterm_002.databinding.FragmentNotificationsBinding
import com.example.thresholdterm_002.ui.ActivityLauncher

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var notificationsViewModel: NotificationsViewModel
    private val mapNavigator = GoogleMapNavigator()
    private val profileStore by lazy { ProfileStore(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        notificationsViewModel = ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        notificationsViewModel.libraries.observe(viewLifecycleOwner) { libraries ->
            binding.textLibraryList.text = libraries.joinToString(separator = "\n\n") {
                "${it.name}\n${it.address}\n${it.openInfo}"
            }
        }
        notificationsViewModel.focusFeedback.observe(viewLifecycleOwner) {
            binding.textFocusFeedback.text = it
        }
        notificationsViewModel.regionText.observe(viewLifecycleOwner) {
            binding.textLibraryRegion.text = it
        }
        binding.buttonLoadLibraries.setOnClickListener {
            notificationsViewModel.loadLibraries(profileStore.getProfile())
        }
        binding.buttonOpenFirstLibraryMap.setOnClickListener {
            val firstLibrary = notificationsViewModel.libraries.value?.firstOrNull()
            if (firstLibrary == null) {
                Toast.makeText(requireContext(), "먼저 도서관 목록을 불러와주세요.", Toast.LENGTH_SHORT).show()
            } else {
                mapNavigator.openPlace(requireContext(), firstLibrary)
            }
        }
        binding.buttonOpenLibraryActivity.setOnClickListener {
            ActivityLauncher.openLibrary(requireContext(), sourceScreen = "도서관 탭")
        }
        binding.buttonCheckPosture.setOnClickListener {
            notificationsViewModel.runSampleFocusCheck()
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            notificationsViewModel.loadLibraries(profileStore.getProfile())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
