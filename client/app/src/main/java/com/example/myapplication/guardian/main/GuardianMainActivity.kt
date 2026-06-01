package com.example.myapplication.guardian.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.guardian.report.ReportFragment
import com.example.myapplication.guardian.schedule.ui.ScheduleFragment
import com.example.myapplication.guardian.settings.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class GuardianMainActivity : AppCompatActivity() {

    private val scheduleFragment = ScheduleFragment()
    private val reportFragment   = ReportFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_main)

        if (savedInstanceState == null) {
            showFragment(scheduleFragment)
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_schedule -> { showFragment(scheduleFragment); true }
                R.id.nav_report   -> { showFragment(reportFragment);   true }
                R.id.nav_settings -> { showFragment(settingsFragment);  true }
                else              -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
