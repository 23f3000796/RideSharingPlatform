package com.example.ridesharingapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ridesharingapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isLoginMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (SessionManager.isLoggedIn()) {
            tryEnterAppFromStoredToken()
        } else {
            setupAuthForm()
        }
    }

    private fun setupAuthForm() {
        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateModeUi()
        }

        binding.btnRegister.setOnClickListener { submitRegister() }
        binding.btnLogin.setOnClickListener { submitLogin() }

        updateModeUi()
    }

    private fun updateModeUi() {
        binding.layoutLogin.visibility = if (isLoginMode) View.VISIBLE else View.GONE
        binding.layoutRegister.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        binding.tvToggleMode.text = if (isLoginMode) {
            "New here? Create an account"
        } else {
            "Already have an account? Sign in"
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressAuth.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnRegister.isEnabled = !loading
    }

    private fun submitRegister() {
        val name = binding.etName.text.toString().trim()
        val rollNo = binding.etRollNo.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        if (name.isEmpty() || rollNo.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Enter your college email"
            return
        }
        // Mirrors the backend rule: 8+ chars, upper + lower case, and a digit or symbol.
        val strong = password.length >= 8 && password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } && password.any { it.isDigit() || !it.isLetterOrDigit() }
        if (!strong) {
            binding.etPassword.error = "Min 8 characters with upper & lower case and a number"
            return
        }
        if (password != confirm) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = RideSharingRepository.register(
                name = name,
                rollNumber = rollNo,
                collegeEmail = email,
                password = password,
                confirmPassword = confirm,
            )
            setLoading(false)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@LoginActivity, "Welcome, $name!", Toast.LENGTH_SHORT).show()
                    goMain()
                },
                onFailure = { e ->
                    Toast.makeText(this@LoginActivity, e.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    private fun submitLogin() {
        val identifier = binding.etIdentifier.text.toString().trim()
        val password = binding.etLoginPassword.text.toString()
        if (identifier.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email or roll number and password.", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val result = RideSharingRepository.login(identifier, password)
            setLoading(false)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@LoginActivity, "Signed in", Toast.LENGTH_SHORT).show()
                    goMain()
                },
                onFailure = { e ->
                    Toast.makeText(this@LoginActivity, e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    private fun tryEnterAppFromStoredToken() {
        setLoading(true)
        lifecycleScope.launch {
            val result = RideSharingRepository.refreshProfile()
            setLoading(false)
            result.fold(
                onSuccess = { goMain() },
                onFailure = { e ->
                    val code = (e as? com.example.ridesharingapp.network.ApiException)?.statusCode
                    if (code == 401 || code == 404) {
                        // Token really is invalid: sign in again.
                        SessionManager.clear()
                        Toast.makeText(this@LoginActivity, "Session expired. Please sign in again.", Toast.LENGTH_SHORT).show()
                        setupAuthForm()
                    } else {
                        // Server unreachable / offline: keep the session and let the user retry.
                        androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                            .setTitle("Can't connect")
                            .setMessage(e.message ?: "The server is not reachable right now.")
                            .setCancelable(false)
                            .setPositiveButton("Retry") { _, _ -> tryEnterAppFromStoredToken() }
                            .setNegativeButton("Sign out") { _, _ -> SessionManager.clear(); setupAuthForm() }
                            .show()
                    }
                },
            )
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
