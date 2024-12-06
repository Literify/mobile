package com.literify.ui.fragment.sign_in

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.snackbar.Snackbar
import com.literify.BuildConfig
import com.literify.R
import com.literify.databinding.FragmentSigninBinding
import com.literify.ui.activity.main.MainActivity
import com.literify.ui.activity.main.MainActivity.Companion.EXTRA_SAVE_CREDENTIAL
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SigninFragment : Fragment() {

    private var _binding: FragmentSigninBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SigninViewModel by viewModels()

    @Inject
    lateinit var credentialManager: CredentialManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSigninBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handleButtonClickListener()
        setupObservers()

        loginWithCredentialsManager()
    }

    private fun handleButtonClickListener() {
        binding.apply {
            buttonSignin.setOnClickListener {
                loginWithEmailPassword()
            }

            buttonSigninGoogle.setOnClickListener {
                loginWithGoogleCredentialsManager()
            }

            buttonForgotPassword.setOnClickListener {
                findNavController().navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
            }

            buttonSignup.setOnClickListener {
                findNavController().navigate(R.id.action_loginFragment_to_signupFragment)
            }
        }
    }

    private fun setupObservers() {
        viewModel.signinState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SigninState.Loading -> {
                    showLoading(true, state.button)
                }
                is SigninState.Success -> {
                    showLoading(false)

                    if(state.user != null) {
                        val intent = Intent(requireContext(), MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        val isEmailLogin = state.user.providerData.any { it.providerId == "password" }
                        if (isEmailLogin) {
                            intent.putExtra(EXTRA_SAVE_CREDENTIAL, true)
                        }

                        startActivity(intent)
                        requireActivity().finish()
                    }
                }
                is SigninState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }
    }

    private fun loginWithCredentialsManager() {
        lifecycleScope.launch {
            val getPasswordOption = GetPasswordOption()
            val getGoogleIdOption: GetGoogleIdOption =
                GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.OAUTH2_BROWSER_CLIENT_ID)
                    .setNonce(System.currentTimeMillis().toString())
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .build()

            try {
                val result = credentialManager.getCredential(
                    context = requireActivity(),
                    request = GetCredentialRequest(listOf(getPasswordOption, getGoogleIdOption))
                )

                when (val credential = result.credential) {
                    is PasswordCredential -> {
                        val id = credential.id
                        val password = credential.password

                        viewModel.loginWithPassword(id, password)
                    }
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                val googleIdTokenCredential =
                                    GoogleIdTokenCredential.createFrom(credential.data)

                                viewModel.loginWithGoogleIdToken(googleIdTokenCredential.idToken)
                            } catch (e: GoogleIdTokenParsingException) {
                                Log.e(TAG, "Failed to parse Google ID Token", e)
                                showError(getString(R.string.error_parsing_google_id))
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun loginWithEmailPassword() {
        val identifier = binding.inputId.editText?.text.toString()
        val password = binding.inputPassword.editText?.text.toString()

        binding.apply {
            inputId.isErrorEnabled = false
            inputPassword.isErrorEnabled = false
        }

        if (identifier.isEmpty()) {
            binding.inputId.error = "${getString(R.string.id)} ${getString(R.string.validation_error_required)}"
        }

        if (password.isEmpty()) {
            binding.inputPassword.error = "${getString(R.string.password)} ${getString(R.string.validation_error_required)}"
        }

        if (identifier.isNotEmpty() && password.isNotEmpty()) {
            viewModel.loginWithPassword(identifier, password)
        }
    }

    private fun loginWithGoogleCredentialsManager() {
        lifecycleScope.launch {
            val getSignInWithGoogleOption: GetSignInWithGoogleOption =
                GetSignInWithGoogleOption.Builder(BuildConfig.OAUTH2_BROWSER_CLIENT_ID)
                    .setNonce(System.currentTimeMillis().toString())
                    .build()

            try {
                val result = credentialManager.getCredential(
                    context = requireActivity(),
                    request = GetCredentialRequest(listOf(getSignInWithGoogleOption))
                )

                val googleIdTokenCredential = GoogleIdTokenCredential
                    .createFrom(result.credential.data)
                viewModel.loginWithGoogleIdToken(googleIdTokenCredential.idToken)
            } catch (_: Exception) { }
        }
    }

    private fun showLoading(show: Boolean, button: String? = null) {
        if (!show) {
            binding.apply {
                buttonSignin.isEnabled = true
                buttonSignin.text = getString(R.string.sign_in)

                buttonSigninGoogle.isEnabled = true
                buttonSigninGoogle.text = getString(R.string.sign_in_with_google)

                buttonForgotPassword.isEnabled = true
                buttonSignup.isEnabled = true

                progressSignin.visibility = View.GONE
                progressSigninGoogle.visibility = View.GONE
            }
            return
        }

        binding.apply {
            buttonSignin.isEnabled = false
            buttonSigninGoogle.isEnabled = false
            buttonForgotPassword.isEnabled = false
            buttonSignup.isEnabled = false
        }

        when (button) {
            "1" -> {
                binding.apply {
                    buttonSignin.text = ""
                    progressSignin.visibility = View.VISIBLE
                }
            }

            "2" -> {
                binding.apply {
                    buttonSigninGoogle.text = ""
                    buttonSigninGoogle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    progressSigninGoogle.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showError(message: String) {
        // TODO: Show error message according to ui/ux plan
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "SigninFragment"
    }
}