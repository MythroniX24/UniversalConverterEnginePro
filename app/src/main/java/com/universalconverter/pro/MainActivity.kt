package com.universalconverter.pro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.universalconverter.pro.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setSupportActionBar(binding.toolbar)
            setupNav()
            requestPerms()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error", e)
        }
    }

    private fun setupNav() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment ?: return
        val nav = navHost.navController
        val appBar = AppBarConfiguration(setOf(R.id.imageFragment, R.id.documentFragment, R.id.mediaFragment, R.id.toolsFragment, R.id.dashboardFragment))
        setupActionBarWithNavController(nav, appBar)
        binding.bottomNav.setupWithNavController(nav)
    }

    private fun requestPerms() {
        val perms = buildList<String> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu); return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_premium) {
            startActivity(android.content.Intent(this, premium.PremiumActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    override fun onSupportNavigateUp(): Boolean {
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment)?.navController
        return nav?.navigateUp() ?: super.onSupportNavigateUp()
    }
}
