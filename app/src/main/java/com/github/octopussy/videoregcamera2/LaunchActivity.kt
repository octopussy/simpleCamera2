package com.github.octopussy.videoregcamera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class LaunchActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        launchMainWithPermissionCheck()
    }

    @NeedsPermission(Manifest.permission.CAMERA)
    fun launchMain() {
        ContextCompat.startActivity(this, Intent(this, MainActivity::class.java), null)
        finish()
    }

    @SuppressLint("NeedOnRequestPermissionsResult")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }
}