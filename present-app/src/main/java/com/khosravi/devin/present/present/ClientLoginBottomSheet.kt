package com.khosravi.devin.present.present

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.FragmentClientLoginBinding

class ClientLoginBottomSheet : BottomSheetDialogFragment() {

    var passwordInputListener: PasswordInputListener? = null

    private var _binding: FragmentClientLoginBinding? = null

    private val binding: FragmentClientLoginBinding
        get() = _binding!!

    private lateinit var correctPassword: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_client_login, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentClientLoginBinding.bind(view)

        val inputPassword = arguments?.getString(KEY_PASSWORD)
        require(!inputPassword.isNullOrEmpty())
        correctPassword = inputPassword

        // Find the views from the inflated layout
        val passwordEditText = binding.passwordEditText
        val passwordInputLayout = binding.passwordInputLayout
        val confirmButton = binding.confirmButton

        // Set up the click listener for the confirm button
        confirmButton.setOnClickListener {
            // Get the entered password from the EditText
            validatePassword(passwordEditText, passwordInputLayout)
        }
        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validatePassword(passwordEditText, passwordInputLayout)
                true
            } else {
                false
            }
        }

    }

    private fun validatePassword(
        passwordEditText: TextInputEditText,
        passwordInputLayout: TextInputLayout
    ) {
        val password = passwordEditText.text?.toString()
        passwordInputLayout.error = null
        passwordInputLayout.isErrorEnabled = false

        if (password == correctPassword) {
            passwordInputListener?.onCorrectPassword(password)
            dismiss()
        } else {
            passwordInputLayout.error = "Incorrect password. Please try again."
            passwordInputLayout.isErrorEnabled = true
            passwordInputListener?.onInCorrectPassword(dialog)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    interface PasswordInputListener {
        fun onCorrectPassword(password: String)
        fun onInCorrectPassword(dialog: Dialog?)
    }

    companion object {
        const val TAG = "ClientLoginBottomSheet"
        private const val KEY_PASSWORD = "password"

        fun newInstance(correctPassword: String) = ClientLoginBottomSheet().apply {
            arguments = bundleOf(KEY_PASSWORD to correctPassword)
        }
    }
}