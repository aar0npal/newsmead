package com.newsmead.activities

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import com.newsmead.databinding.ActivityAccountBinding
import com.newsmead.fragments.account.LogInFragment
import com.newsmead.fragments.account.SignUpFragment

class AccountActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityAccountBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.viewBinding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)


        if (savedInstanceState != null) return

        val fragment = if ("sign_up" == intent?.getStringExtra("initial_fragment")) {
            SignUpFragment()
        } else {
            LogInFragment.newInstance(intent?.getStringExtra("notice"))
        }

        supportFragmentManager.beginTransaction()
            .replace(viewBinding.flAccountContainer.id, fragment)
            .commit()
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        }
    }
}
