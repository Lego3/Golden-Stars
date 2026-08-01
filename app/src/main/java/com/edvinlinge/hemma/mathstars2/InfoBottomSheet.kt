package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class InfoBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.layout_info_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val message = arguments?.getString(ARG_MESSAGE) ?: ""
        val infoMessage = view.findViewById<TextView>(R.id.infoMessage)
        infoMessage.text = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)

        view.findViewById<Button>(R.id.closeButton).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "InfoBottomSheet"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: String): InfoBottomSheet {
            return InfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                }
            }
        }
    }
}
