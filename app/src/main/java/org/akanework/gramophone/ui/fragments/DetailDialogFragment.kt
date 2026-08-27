package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.LibraryViewModel

class DetailDialogFragment : BaseFragment(true) {

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_info_song, container, false)
        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        rootView.findViewById<View>(R.id.scrollView).enableEdgeToEdgePaddingListener()
        rootView.findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }
        val mediaItem =
            libraryViewModel.mediaItemList.value!![requireArguments().getInt("Position")]
        val mediaMetadata = mediaItem.mediaMetadata
        val albumCoverImageView = rootView.findViewById<ImageView>(R.id.album_cover)
        val titleTextView = rootView.findViewById<TextView>(R.id.title)
        val artistTextView = rootView.findViewById<TextView>(R.id.artist)
        val albumArtistTextView = rootView.findViewById<TextView>(R.id.album_artist)
        val albumTextView = rootView.findViewById<TextView>(R.id.album)
        val yearTextView = rootView.findViewById<TextView>(R.id.year)
        val durationTextView = rootView.findViewById<TextView>(R.id.duration)
        val mimeTypeTextView = rootView.findViewById<TextView>(R.id.mime)
        albumCoverImageView.load(mediaMetadata.artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
        }
        titleTextView.text = mediaMetadata.title
        artistTextView.text = mediaMetadata.artist
        albumTextView.text = mediaMetadata.albumTitle
        if (mediaMetadata.albumArtist != null) {
            albumArtistTextView.text = mediaMetadata.albumArtist
        }
        yearTextView.text = mediaMetadata.releaseYear?.toString()
            ?: getString(R.string.unknown_year)
        // MediaStore didn't have a year for this song - as a last resort, read it straight off
        // the file's own tags. Deliberately done here (on demand, for just this one song) rather
        // than during the library scan, where opening/parsing every file this way would be slow.
        if (mediaMetadata.releaseYear == null) {
            mediaItem.getFile()?.path?.let { path ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val year = MediaStoreUtils.extractYearFromTags(path) ?: return@launch
                    withContext(Dispatchers.Main) {
                        yearTextView.text = year.toString()
                    }
                }
            }
        }
        durationTextView.text =
            convertDurationToTimeStamp(mediaMetadata.extras!!.getLong("Duration"))
        mimeTypeTextView.text = formatMimeType(mediaItem.localConfiguration?.mimeType)
        return rootView
    }

    // "audio/flac" -> "FLAC", "audio/mpeg" -> "MPEG", etc.
    private fun formatMimeType(mimeType: String?): String {
        return mimeType?.substringAfterLast('/')?.uppercase() ?: "(null)"
    }
}