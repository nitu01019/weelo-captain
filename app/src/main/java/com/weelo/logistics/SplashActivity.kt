package com.weelo.logistics

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.weelo.logistics.data.remote.RetrofitClient
import java.util.concurrent.Executors

/**
 * SplashActivity - INSTANT LOAD like Rapido
 * 
 * Uses XML layout (not Compose) for INSTANT rendering
 * - "Weelo Captain" shows the MOMENT you tap the app
 * - 2 seconds total display
 * - Then navigates to MainActivity
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Check login in background thread
        executor.execute {
            val isLoggedIn = RetrofitClient.isLoggedIn()
            val userRole = RetrofitClient.getUserRole()
            
            // Wait 1 second then navigate on main thread
            handler.postDelayed({
                val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                    putExtra("IS_LOGGED_IN", isLoggedIn)
                    putExtra("USER_ROLE", userRole)
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 1000)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
