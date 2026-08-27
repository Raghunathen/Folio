/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui

import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.preference.PreferenceManager
import coil3.imageLoader
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.logic.utils.ArtistImageStore
import org.akanework.gramophone.logic.utils.MediaStoreUtils.updateLibraryWithInCoroutine
import org.akanework.gramophone.ui.components.PlayerBottomSheet
import org.akanework.gramophone.ui.fragments.BaseFragment
import org.akanework.gramophone.ui.fragments.BaseWrapperFragment
import org.akanework.gramophone.ui.fragments.ViewPagerFragment

/**
 * MainActivity:
 *   Core of gramophone, one and the only activity
 * used across the application.
 *
 * @author AkaneTan, nift4
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
        private const val PERMISSION_READ_MEDIA_IMAGES = 101
        private const val ASKED_IMAGES_PERMISSION = "asked_images_permission"
        const val PLAYBACK_AUTO_START_FOR_FGS = "AutoStartFgs"
    }

    // Import our viewModels.
    val libraryViewModel: LibraryViewModel by viewModels()
    val startingActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    private val handler = Handler(Looper.getMainLooper())
    private val reportFullyDrawnRunnable = Runnable { if (!ready) reportFullyDrawn() }
    private var ready = false
    private var reportFullyDrawnCalled = false
    private var autoPlay = false
    lateinit var playerBottomSheet: PlayerBottomSheet
        private set
    private lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>
    lateinit var bottomNavigationView: BottomNavigationView
    private var intentSenderAction: (() -> Boolean)? = null

    private lateinit var container: FragmentContainerView

    /**
     * updateLibrary:
     *   Calls [updateLibraryWithInCoroutine] in MediaStoreUtils and updates library.
     */
    fun updateLibrary(then: (() -> Unit)? = null) {
        // If library load takes more than 3s, exit splash to avoid ANR
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 3000)
        CoroutineScope(Dispatchers.Default).launch {
            updateLibraryWithInCoroutine(libraryViewModel, this@MainActivity) {
                if (!ready) reportFullyDrawn()
                then?.let { it() }
            }
        }
    }

    /**
     * onCreate - core of MainActivity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        enableEdgeToEdgeProperly()
        super.onCreate(savedInstanceState)
        autoPlay = intent?.extras?.getBoolean(PLAYBACK_AUTO_START_FOR_FGS, false) == true
        intentSender =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == RESULT_OK) {
                    if (intentSenderAction != null) {
                        intentSenderAction!!()
                    } else {
                        Toast.makeText(
                            this, getString(
                                R.string.delete_in_progress
                            ), Toast.LENGTH_LONG
                        ).show()
                    }
                }
                intentSenderAction = null
            }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object :
            FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                super.onFragmentStarted(fm, f)
                if (fm.fragments.lastOrNull() != f) return
                // this won't be called in case we show()/hide() so
                // we handle that case in BaseFragment
                if (f is BaseFragment && f.wantsPlayer != null) {
                    playerBottomSheet.visible = f.wantsPlayer
                }
            }
        }, false)

        // Set content Views.
        setContentView(R.layout.activity_main)
        window.decorView.setBackgroundColor(
            MaterialColors.getColor(
                window.decorView,
                R.attr.contrast_colorBackground
            )
        )
        playerBottomSheet = findViewById(R.id.player_layout)
        bottomNavigationView = findViewById(R.id.bottom_nav)
        container = findViewById(R.id.container)

        // Modifies FragmentContainerView's insets to account for bottom sheet size.
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            playerBottomSheet.generateBottomSheetInsets(insets)
        }

        if (savedInstanceState != null) {
            val translationY = savedInstanceState.getFloat("bottomNavigationTranslationY")
            bottomNavigationView.translationY = translationY
        }

        // Check all permissions.
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_MEDIA_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED)
            || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                    && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED)
            || (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            // Ask if was denied.
            ActivityCompat.requestPermissions(
                this,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                else
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                PERMISSION_READ_MEDIA_AUDIO,
            )
        } else {
            // If all permissions are granted, we can update library now.
            if (libraryViewModel.mediaItemList.value == null) {
                updateLibrary()
            } else reportFullyDrawn() // <-- when recreating activity due to rotation
            maybeRequestArtistImagePermission()
        }
    }

    /**
     * Artist photos are ordinary image files in Music/artists, so reading them needs the images
     * permission - the audio permission asked for above doesn't cover them. Asked once, and only
     * after the audio prompt has been answered so the two dialogs can't collide. Declining is
     * fine: artist pages simply fall back to album covers. Behaviour settings' "album covers"
     * entry stays the way to change the answer afterwards.
     *
     * Note this reads the folder directly rather than through MediaStore, so a .nomedia file in
     * Music/artists (which hides the photos from gallery apps) makes no difference to us.
     */
    private fun maybeRequestArtistImagePermission() {
        // Below Android 13 the storage permission requested above already covers images.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasImagePermission()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(ASKED_IMAGES_PERMISSION, false)) return
        prefs.edit { putBoolean(ASKED_IMAGES_PERMISSION, true) }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
            PERMISSION_READ_MEDIA_IMAGES,
        )
    }

    // https://twitter.com/Piwai/status/1529510076196630528
    override fun reportFullyDrawn() {
        handler.removeCallbacks(reportFullyDrawnRunnable)
        // Multiple call sites race to invoke this (3s ANR-avoidance fallback, library-load
        // completion, rotation-recreate path) and `ready` only flips true a couple frames after
        // reportFullyDrawnCalled is set, so a redundant call landing in that window is expected
        // under load - just ignore it instead of crashing.
        if (reportFullyDrawnCalled) return
        reportFullyDrawnCalled = true
        // The library LiveData is populated by this point, but the Songs list's RecyclerView
        // hasn't necessarily finished inflating/binding it yet - notifyDataSetChanged() and the
        // resulting layout pass land on a later frame, not synchronously here. Flipping `ready`
        // (what the splash screen's keepOnScreenCondition checks) immediately could let the
        // splash exit and reveal a still-empty list for a frame or two before it catches up.
        // Give it a couple of frames to settle first.
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback {
                ready = true
                handler.postAtFrontOfQueueAsync {
                    super.reportFullyDrawn()
                }
            }
        }
    }

    fun retractNavigationViewWithProgress(progressHeight: Float) {
        bottomNavigationView.translationY = progressHeight
    }

    /**
     * onRequestPermissionResult:
     *   Update library after permission is granted.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_READ_MEDIA_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                updateLibrary()
            } else {
                reportFullyDrawn()
                // TODO: Show a prompt here
            }
            maybeRequestArtistImagePermission()
        } else if (requestCode == PERMISSION_READ_MEDIA_IMAGES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasImagePermission()) {
                // Anything already showing an album-cover fallback should pick the real photo up.
                ArtistImageStore.invalidate()
            } else if (grantResults.isNotEmpty() &&
                grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            ) {
                // Android 14+ offers "Select photos" instead of full access, which grants only
                // READ_MEDIA_VISUAL_USER_SELECTED - that covers individually picked photos, not
                // a folder we read ourselves, so artist photos still won't load. Say so once
                // rather than leaving it looking broken.
                Toast.makeText(this, R.string.artist_images_partial_access, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            // https://issuetracker.google.com/issues/181316771 - known AOSP FocusFinder bug:
            // focus search can return a view (e.g. one recycled mid-search inside a
            // RecyclerView) that is no longer able to take focus by the time it's requested.
            // Not something app code can prevent - swallow it rather than crash.
            if (e.message?.contains("focus search returned a view") == true) true else throw e
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("bottomNavigationTranslationY", bottomNavigationView.translationY)
    }

    /**
     * startFragment:
     *   Used by child fragments / drawer to start
     * a fragment inside MainActivity's fragment
     * scope.
     *
     * @param frag: Target fragment.
     */
    fun startFragment(frag: Fragment, args: (Bundle.() -> Unit)? = null) {
        supportFragmentManager
            .beginTransaction()
            .addToBackStack(System.currentTimeMillis().toString())
            .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
            .add(R.id.container, frag.apply { args?.let { arguments = Bundle().apply(it) } })
            .commit()
    }

    /**
     * currentWrapperFragment:
     *   Fragments like GeneralSubFragment/ArtistSubFragment/DetailDialogFragment expect to be
     * nested inside the currently visible tab's BaseWrapperFragment (they navigate back via
     * requireParentFragment()), unlike startFragment()'s targets which manage their own back
     * stack directly. Used by callers outside the fragment tree (e.g. the full player) that need
     * to push one of those onto whichever tab (Library/Folders) is currently shown.
     */
    fun currentWrapperFragment(): BaseWrapperFragment? {
        val position = when (bottomNavigationView.selectedItemId) {
            R.id.browse -> 0
            R.id.folders -> 1
            else -> return null
        }
        val viewPagerFragment = supportFragmentManager.fragments
            .filterIsInstance<ViewPagerFragment>().firstOrNull() ?: return null
        return viewPagerFragment.childFragmentManager
            .findFragmentByTag("f$position") as? BaseWrapperFragment
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        // https://github.com/androidx/media/issues/805
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            && (getPlayer()?.playWhenReady != true || getPlayer()?.mediaItemCount == 0)
        ) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        super.onDestroy()
        // we don't ever want covers to be the cause of service being killed by too high mem usage
        // (this is placed after super.onDestroy() to make sure all ImageViews are dead)
        imageLoader.memoryCache?.clear()
    }

    fun scaleContainer(factor: Float) {
        container.scaleX = 1f - factor * 0.10f
        container.scaleY = 1f - factor * 0.10f
    }

    /**
     * getPlayer:
     *   Returns a media controller.
     */
    fun getPlayer() = playerBottomSheet.getPlayer()

    fun consumeAutoPlay(): Boolean {
        return autoPlay.also { autoPlay = false }
    }
}
