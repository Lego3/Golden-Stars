package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.parseAsHtml
import com.edvinlinge.hemma.mathstars2.databinding.LayoutInfoBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Shared Details / Help sheet. The host supplies both the title and the message so the same
 * layout can describe a figure (Details) or explain how a screen works (Help).
 */
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

        val arguments = requireArguments()
        binding.infoTitle.text = infoSheetTitle(
            arguments.getString(ARG_TITLE),
            getString(R.string.more_info_button),
        )
        val message = arguments.getString(ARG_MESSAGE) ?: ""
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
        private const val ARG_TITLE = "arg_title"

        fun newInstance(message: String, title: String): InfoBottomSheet {
            val args = Bundle().apply {
                putString(ARG_MESSAGE, message)
                putString(ARG_TITLE, title)
            }
            return InfoBottomSheet().apply { arguments = args }
        }
    }
}
