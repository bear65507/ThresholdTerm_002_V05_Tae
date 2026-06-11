package com.example.thresholdterm_002.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.thresholdterm_002.data.model.DailyStudyStat
import com.example.thresholdterm_002.databinding.FragmentDashboardBinding
import com.example.thresholdterm_002.ui.ActivityLauncher

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        dashboardViewModel.summaryText.observe(viewLifecycleOwner) {
            binding.textStudySummary.text = it
        }
        dashboardViewModel.leaderboardText.observe(viewLifecycleOwner) {
            binding.textLeaderboard.text = it
        }
        dashboardViewModel.dailyStats.observe(viewLifecycleOwner) {
            renderDailyChart(it)
        }
        binding.buttonRefreshDashboard.setOnClickListener {
            dashboardViewModel.refresh()
        }
        binding.buttonOpenStatsActivity.setOnClickListener {
            ActivityLauncher.openStats(requireContext(), sourceScreen = "기록 탭")
        }
        dashboardViewModel.refresh()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderDailyChart(stats: List<DailyStudyStat>) {
        binding.dailyChartContainer.removeAllViews()
        stats.forEach { stat ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }
            val label = TextView(requireContext()).apply {
                text = "${stat.label}  ${stat.totalMinutes}/${stat.goalMinutes}분  집중도 ${stat.averageFocusScore}점"
                textSize = 14f
            }
            val progress = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = stat.goalMinutes
                progress = stat.totalMinutes.coerceAtMost(stat.goalMinutes)
            }
            row.addView(label)
            row.addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            binding.dailyChartContainer.addView(row)
        }
    }
}
