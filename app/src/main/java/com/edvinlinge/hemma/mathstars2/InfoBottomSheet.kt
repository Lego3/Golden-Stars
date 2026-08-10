package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.parseAsHtml
import com.edvinlinge.hemma.mathstars2.databinding.LayoutInfoBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class InfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutInfoBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = LayoutInfoBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val message = arguments?.getString(ARG_MESSAGE) ?: ""
        binding.infoMessage.text = if (shouldParseInfoMessageAsHtml(message)) {
            message.parseAsHtml()
        } else {
            message
        }
        binding.closeButton.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "InfoBottomSheet"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: String): InfoBottomSheet {
            val args = Bundle().apply { putString(ARG_MESSAGE, message) }
            return InfoBottomSheet().apply { arguments = args }
        }
    }
}
