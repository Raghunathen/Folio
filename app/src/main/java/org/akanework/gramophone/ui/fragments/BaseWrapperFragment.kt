package org.akanework.gramophone.ui.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import org.akanework.gramophone.R

class BaseWrapperFragment : BaseFragment {

    private var fragmentType: Int = 0

    constructor() : super()

    constructor(fragmentType: Int) : super() {
        this.fragmentType = fragmentType
    }

    private var backCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_wrapper, container, false)
        if (childFragmentManager.fragments.isEmpty()) {
            childFragmentManager
                .beginTransaction()
                .replace(
                    R.id.wrapper_container,
                    when (fragmentType) {
                        0 -> BrowseFragment()
                        1 -> FoldersFragment()
                        else -> throw IllegalArgumentException()
                    }
                )
                .commit()
        }
        return rootView
    }

    // Tapping the bottom nav's already-selected tab (see ViewPagerFragment) should behave like
    // most apps' "tap the active tab to jump back to its root" - pop every pushed sub-page
    // (Album/Artist/Genre/etc.) at once rather than needing repeated back presses.
    fun popToRoot() {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack(
                childFragmentManager.getBackStackEntryAt(0).id,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
        backCallback?.isEnabled = false
    }

    fun replaceFragment(frag: BaseFragment, args: (Bundle.() -> Unit)? = null) {
        Log.d("TAG", "B4ADD, ${childFragmentManager.fragments.size}")
        childFragmentManager.beginTransaction()
            .addToBackStack(System.currentTimeMillis().toString())
            .hide(childFragmentManager.fragments.let { it[it.size - 1] })
            .add(
                R.id.wrapper_container,
                frag.apply { args?.let { arguments = Bundle().apply(it) } })
            .commit()
        backCallback!!.isEnabled = true
        Log.d("TAG", "ADD, ${childFragmentManager.fragments.size}")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Log.d("TAG", "BASEWRAPPED!")
                childFragmentManager.popBackStack()
                if (childFragmentManager.backStackEntryCount == 1) {
                    backCallback!!.isEnabled = false
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    override fun onDetach() {
        super.onDetach()
        backCallback!!.remove()
    }
}