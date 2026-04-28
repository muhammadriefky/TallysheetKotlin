package com.example.handheldapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.example.handheldapp.databinding.ActivitySplashBinding
import com.example.handheldapp.ui.base.BaseActivity
import com.example.handheldapp.utils.AppStatusManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SplashActivity - Modern Animated Splash Screen
 *
 * Features:
 * - Animated Logo with rotating rings
 * - Floating particle effects
 * - Text reveal animations
 * - Feature highlights with stagger animation
 * - Loading dots animation
 * - Smooth transition to LoginActivity
 * - **APP STATUS CHECK** - Block access during CRITICAL maintenance
 */
@AndroidEntryPoint
class Splash : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    @Inject
    lateinit var appStatusManager: AppStatusManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start animations
        startAnimations()

        // Check app status and navigate after delay
        handler.postDelayed({
            checkAppStatusAndNavigate()
        }, 4000) // 4 seconds total
    }

    /**
     * Check app status sebelum navigate
     * ★ SIMPLIFIED: Semua handling maintenance dipindah ke LoginActivity dengan UI baru
     * SplashActivity hanya log status dan langsung navigate
     */
    private fun checkAppStatusAndNavigate() {
        lifecycleScope.launch {
            try {
                val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
                val status = appStatusManager.checkAppStatus(versionCode)

                android.util.Log.d("SplashActivity", "App status: $status")
                android.util.Log.d("SplashActivity", "Maintenance type: ${appStatusManager.getMaintenanceType()}")
                android.util.Log.d("SplashActivity", "Offline work allowed: ${appStatusManager.isOfflineWorkAllowed()}")

                // ★ Langsung navigate ke LoginActivity
                // Semua handling maintenance (critical/major/minor) dilakukan di LoginActivity dengan UI baru
                navigateToLogin()

            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error checking status: ${e.message}")
                // Jika error check status, tetap lanjut ke login (biar offline mode bisa jalan)
                navigateToLogin()
            }
        }
    }

    private fun startAnimations() {
        // Phase 1: Logo entrance (0-800ms)
        animateLogoEntrance()

        // Phase 2: Ring animations (continuous)
        animateRings()

        // Phase 3: Text reveal (600ms)
        handler.postDelayed({ animateTextReveal() }, 600)

        // Phase 4: Features slide in (1200ms)
        handler.postDelayed({ animateFeatures() }, 1200)

        // Phase 5: Loading dots (1800ms)
        handler.postDelayed({ animateLoadingDots() }, 1800)

        // Phase 6: Particle floating (continuous)
        animateParticles()
    }

    private fun animateLogoEntrance() {
        val cardLogo = binding.cardLogo

        // Initial state
        cardLogo.scaleX = 0f
        cardLogo.scaleY = 0f
        cardLogo.alpha = 0f

        // Animate with overshoot
        val scaleX = ObjectAnimator.ofFloat(cardLogo, View.SCALE_X, 0f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardLogo, View.SCALE_Y, 0f, 1.1f, 1f)
        val alpha = ObjectAnimator.ofFloat(cardLogo, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 800
            interpolator = OvershootInterpolator(2f)
            start()
        }

        // Pulse animation for logo (starts after initial animation)
        handler.postDelayed({
            val pulseX = ObjectAnimator.ofFloat(cardLogo, View.SCALE_X, 1f, 1.05f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
            val pulseY = ObjectAnimator.ofFloat(cardLogo, View.SCALE_Y, 1f, 1.05f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
            AnimatorSet().apply {
                playTogether(pulseX, pulseY)
                start()
            }
        }, 800)
    }

    private fun animateRings() {
        // Outer ring - slow rotation
        ObjectAnimator.ofFloat(binding.ringOuter, View.ROTATION, 0f, 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Middle ring - faster counter-rotation
        ObjectAnimator.ofFloat(binding.ringMiddle, View.ROTATION, 0f, -360f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Fade in rings
        ObjectAnimator.ofFloat(binding.ringOuter, View.ALPHA, 0f, 0.3f).apply {
            duration = 1000
            startDelay = 300
            start()
        }
        ObjectAnimator.ofFloat(binding.ringMiddle, View.ALPHA, 0f, 0.5f).apply {
            duration = 1000
            startDelay = 500
            start()
        }
    }

    private fun animateTextReveal() {
        // App name slide up + fade in
        binding.tvAppName.apply {
            translationY = 30f
            ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 30f, 0f).apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f).apply {
                duration = 500
                start()
            }
        }

        // Subtitle with delay
        handler.postDelayed({
            binding.tvAppSubtitle.apply {
                translationY = 20f
                ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 20f, 0f).apply {
                    duration = 400
                    start()
                }
                ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f).apply {
                    duration = 400
                    start()
                }
            }
        }, 200)

        // Tagline with more delay
        handler.postDelayed({
            binding.tvTagline.apply {
                ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f).apply {
                    duration = 500
                    start()
                }
            }
        }, 400)
    }

    private fun animateFeatures() {
        val features = binding.containerFeatures

        features.translationY = 50f
        ObjectAnimator.ofFloat(features, View.TRANSLATION_Y, 50f, 0f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(features, View.ALPHA, 0f, 1f).apply {
            duration = 600
            start()
        }

        // Stagger animation for each feature card (now CardViews)
        listOf(binding.featureScan, binding.featureRealtime, binding.featureTracking)
            .forEachIndexed { index, cardView ->
                handler.postDelayed({
                    cardView.scaleX = 0.7f
                    cardView.scaleY = 0.7f
                    cardView.alpha = 0f

                    // Scale animation
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(cardView, View.SCALE_X, 0.7f, 1.05f, 1f).apply {
                                duration = 400
                                interpolator = OvershootInterpolator(1.5f)
                            },
                            ObjectAnimator.ofFloat(cardView, View.SCALE_Y, 0.7f, 1.05f, 1f).apply {
                                duration = 400
                                interpolator = OvershootInterpolator(1.5f)
                            },
                            ObjectAnimator.ofFloat(cardView, View.ALPHA, 0f, 1f).apply {
                                duration = 350
                            }
                        )
                        start()
                    }

                    // Add subtle pulse animation after entrance
                    handler.postDelayed({
                        val pulseAnimator = ObjectAnimator.ofFloat(cardView, View.SCALE_X, 1f, 1.03f, 1f).apply {
                            duration = 2000
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.REVERSE
                        }
                        val pulseY = ObjectAnimator.ofFloat(cardView, View.SCALE_Y, 1f, 1.03f, 1f).apply {
                            duration = 2000
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.REVERSE
                        }
                        AnimatorSet().apply {
                            playTogether(pulseAnimator, pulseY)
                            start()
                        }
                    }, 500)

                }, (index * 180).toLong())
            }
    }

    private fun animateLoadingDots() {
        // Fade in loading container
        ObjectAnimator.ofFloat(binding.loadingContainer, View.ALPHA, 0f, 1f).apply {
            duration = 300
            start()
        }
        ObjectAnimator.ofFloat(binding.tvLoadingText, View.ALPHA, 0f, 1f).apply {
            duration = 300
            start()
        }
        ObjectAnimator.ofFloat(binding.tvVersion, View.ALPHA, 0f, 1f).apply {
            duration = 300
            start()
        }

        // Bouncing dots animation
        val dots = listOf(binding.loadingDot1, binding.loadingDot2, binding.loadingDot3)
        dots.forEachIndexed { index, dot ->
            val animator = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -15f, 0f).apply {
                duration = 600
                startDelay = (index * 150).toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            animator.start()
        }

        // Update loading text
        val loadingTexts = listOf(
            "Mempersiapkan sistem...",
            "Memeriksa koneksi...",
            "Hampir selesai..."
        )
        var textIndex = 0
        val textUpdater = object : Runnable {
            override fun run() {
                if (textIndex < loadingTexts.size) {
                    binding.tvLoadingText.text = loadingTexts[textIndex]
                    textIndex++
                    handler.postDelayed(this, 700)
                }
            }
        }
        handler.postDelayed(textUpdater, 500)
    }

    private fun animateParticles() {
        // Floating animation for particles
        val particles = listOf(
            binding.particleView1,
            binding.particleView2,
            binding.particleView3,
            binding.particleView4
        )

        particles.forEachIndexed { index, particle ->
            val translateY = ObjectAnimator.ofFloat(
                particle, View.TRANSLATION_Y,
                0f, (20 + index * 8).toFloat(), 0f
            ).apply {
                duration = (3000 + index * 400).toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }

            val translateX = ObjectAnimator.ofFloat(
                particle, View.TRANSLATION_X,
                0f, (15 - index * 4).toFloat(), 0f
            ).apply {
                duration = (2500 + index * 300).toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }

            AnimatorSet().apply {
                playTogether(translateY, translateX)
                start()
            }
        }
    }

    private fun navigateToLogin() {
        // Exit animation
        val fadeOut = ObjectAnimator.ofFloat(binding.root, View.ALPHA, 1f, 0f).apply {
            duration = 400
        }

        fadeOut.doOnEnd {
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        fadeOut.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
