package com.example.thresholdterm_002.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.thresholdterm_002.databinding.FragmentHomeBinding
import com.example.thresholdterm_002.ui.ActivityLauncher

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        homeViewModel.timerText.observe(viewLifecycleOwner) {
            binding.textTimer.text = it
        }
        homeViewModel.statusText.observe(viewLifecycleOwner) {
            binding.textFocusStatus.text = it
        }
        homeViewModel.focusInsightText.observe(viewLifecycleOwner) {
            binding.textFocusInsight.text = it
        }
        homeViewModel.selectedMinutesText.observe(viewLifecycleOwner) {
            binding.textSelectedDuration.text = it
        }

        binding.chip15Minutes.setOnClickListener {
            homeViewModel.selectDuration(15)
        }
        binding.chip25Minutes.setOnClickListener {
            homeViewModel.selectDuration(25)
        }
        binding.chip50Minutes.setOnClickListener {
            homeViewModel.selectDuration(50)
        }

        binding.buttonStart.setOnClickListener {
            homeViewModel.startFocusSession()
        }
        binding.buttonPause.setOnClickListener {
            homeViewModel.pauseFocusSession()
        }
        binding.buttonSaveStop.setOnClickListener {
            homeViewModel.stopAndSaveFocusSession()
        }
        binding.buttonReset.setOnClickListener {
            homeViewModel.resetFocusSession()
        }
        binding.buttonOpenTimerActivity.setOnClickListener {
            ActivityLauncher.openTimer(requireContext(), sourceScreen = "메인 타이머 탭")
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
