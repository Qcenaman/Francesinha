package com.qcenaman.francesinha.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.service.TrackingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent { FrancesinhaTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        TrackingService.refresh(this)
    }
}
