package uz.yangiuzbkey.keyboard.settings

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import uz.yangiuzbkey.keyboard.R
import android.Manifest

/** Invisible activity: a keyboard cannot ask for runtime permissions itself. */
class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { done(true); return }
        if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        done(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }

    private fun done(granted: Boolean) {
        Toast.makeText(this, if (granted) R.string.voice_granted else R.string.voice_denied, Toast.LENGTH_SHORT).show()
        finish()
    }
}
