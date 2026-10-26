package uz.yangiuzbkey.keyboard.settings

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.ime.ScreenshotStore
import uz.yangiuzbkey.keyboard.util.Prefs

/** Invisible activity: a keyboard cannot ask for runtime permissions itself. */
class ScreenshotPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(ScreenshotStore.PERMISSION) == PackageManager.PERMISSION_GRANTED) { done(true); return }
        if (savedInstanceState == null) requestPermissions(arrayOf(ScreenshotStore.PERMISSION), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        done(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }

    private fun done(granted: Boolean) {
        Prefs(this).screenshots = granted
        Toast.makeText(this, if (granted) R.string.clip_screens_on else R.string.clip_screens_off, Toast.LENGTH_SHORT).show()
        finish()
    }
}
