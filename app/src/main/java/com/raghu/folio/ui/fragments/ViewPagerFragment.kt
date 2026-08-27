package com.raghu.folio.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.raghu.folio.R
import com.raghu.folio.ui.MainActivity
import com.raghu.folio.ui.adapters.MainPageAdapter

class ViewPagerFragment : BaseFragment(true) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = layoutInflater.inflate(R.layout.fragment_viewpager, container, false)
        val mViewPager2: ViewPager2 = rootView.findViewById(R.id.viewpager2)
        val adapter = MainPageAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)

        mViewPager2.adapter = adapter
        mViewPager2.isUserInputEnabled = false
        mViewPager2.offscreenPageLimit = 9999

        val bottomNavigationView = (requireActivity() as MainActivity).bottomNavigationView

        bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.browse -> mViewPager2.setCurrentItem(0, true)
                R.id.folders -> mViewPager2.setCurrentItem(1, true)
                else -> throw IllegalArgumentException("Illegal itemId: ${it.itemId}")
            }
            true
        }

        // BottomNavigationView only calls setOnItemSelectedListener for an item that wasn't
        // already selected - tapping the current tab again needs this separate callback instead,
        // to pop back out of whatever Album/Artist/etc. sub-page is currently showing.
        bottomNavigationView.setOnItemReselectedListener {
            (requireActivity() as MainActivity).currentWrapperFragment()?.popToRoot()
        }

        mViewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> bottomNavigationView.selectedItemId = R.id.browse
                    1 -> bottomNavigationView.selectedItemId = R.id.folders
                }
            }
        })

        return rootView
    }
}