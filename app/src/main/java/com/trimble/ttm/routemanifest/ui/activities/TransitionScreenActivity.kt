package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.trimble.ttm.routemanifest.databinding.ActivityTransitionScreenBinding

class TransitionScreenActivity : AppCompatActivity() {
    private val timeout : Long = 1000
    private lateinit var activity: ActivityTransitionScreenBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = ActivityTransitionScreenBinding.inflate(layoutInflater)
        setContentView(activity.root)
        Handler(Looper.getMainLooper()).postDelayed({
            Intent(this,DispatchListActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(this)
            }
            finish()
        }, timeout)
    }
}