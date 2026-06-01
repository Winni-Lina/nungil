package com.example.myapplication.user.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.RoleSelectActivity

class UserSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_user_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs  = requireContext().getSharedPreferences("nungil_prefs", 0)
        val userId = prefs.getString("user_id", "")
        view.findViewById<TextView>(R.id.tvUserId).text = userId

        view.findViewById<View>(R.id.layoutLogout).setOnClickListener {
            prefs.edit()
                .remove("user_id")
                .remove("user_idx")
                .apply()
            startActivity(Intent(requireContext(), RoleSelectActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
