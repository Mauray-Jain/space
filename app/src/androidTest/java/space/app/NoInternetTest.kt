package space.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

// CLAUDE.md: the manifest has no INTERNET permission, including anything a library merges in.
@RunWith(AndroidJUnit4::class)
class NoInternetTest {
    @Test
    fun appDoesNotRequestInternet() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
        val requested = info.requestedPermissions.orEmpty().toList()
        assertFalse(requested.toString(), Manifest.permission.INTERNET in requested)
    }
}
