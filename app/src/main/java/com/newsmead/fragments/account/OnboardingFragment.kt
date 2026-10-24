package com.newsmead.fragments.account

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.newsmead.data.DataHelper.loadCategoryLongerData
import com.newsmead.activities.MainActivity
import com.newsmead.data.FirebaseHelper
import com.newsmead.logging.Analytics
import com.newsmead.research.ResearchSession
import com.newsmead.databinding.ChipOnboardingBinding

import com.newsmead.databinding.FragmentOnboardingBinding

/**
 * A simple [Fragment] subclass.
 * Use the [OnboardingFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class OnboardingFragment : Fragment() {
    private lateinit var binding: FragmentOnboardingBinding
    private lateinit var checkedChipTexts: List<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false)

        val chipData = loadCategoryLongerData()
        val chipGroup = binding.cgCategoryTopics

        checkedChipTexts = mutableListOf<String>()

        for (category in chipData) {
            val chip = ChipOnboardingBinding.inflate(inflater, chipGroup, false).root
            chip.text = category

            // Make chips toggleable
            chip.isCheckable = true

            // Add listener to enable button when 3 categories are selected
            chip.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    (checkedChipTexts as MutableList<String>).add(buttonView.text.toString().lowercase())
                } else {
                    (checkedChipTexts as MutableList<String>).remove(buttonView.text.toString().lowercase())
                }

                if (checkedChipTexts.size >= 3) {
                    binding.btnFinishOnboarding.isEnabled = true
                    binding.btnFinishOnboarding.alpha = 1.0f
                } else {
                    binding.btnFinishOnboarding.isEnabled = false
                    binding.btnFinishOnboarding.alpha = 0.5f
                }
            }

            chipGroup.addView(chip)
        }

        // Disable button if less than 3 categories are selected
        binding.btnFinishOnboarding.isEnabled = false
        binding.btnFinishOnboarding.alpha = 0.5f

        // Button for onboarding
        binding.btnFinishOnboarding.setOnClickListener {
            // Add selected categories to Firebase
            FirebaseHelper.addPreferredCategoriesToFireStore(requireActivity(), checkedChipTexts)
            Analytics.onboardingComplete(checkedChipTexts.size)
            ResearchSession.onboardingComplete(checkedChipTexts.size)

            val intent = Intent(requireActivity(), MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            requireActivity().startActivity(intent)
            requireActivity().finish()
        }

        return binding.root
    }
}