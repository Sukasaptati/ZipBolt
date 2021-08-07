package com.salesground.zipbolt.ui.fragments

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.salesground.zipbolt.MainActivity
import com.salesground.zipbolt.broadcast.SendDataBroadcastReceiver
import com.salesground.zipbolt.databinding.FragmentVideosBinding
import com.salesground.zipbolt.ui.recyclerview.DataToTransferRecyclerViewItemClickListener
import com.salesground.zipbolt.ui.recyclerview.HalfLineRecyclerViewCustomDivider
import com.salesground.zipbolt.ui.recyclerview.videoFragment.VideoFragmentRecyclerViewAdapter
import com.salesground.zipbolt.viewmodel.DataToTransferViewModel
import com.salesground.zipbolt.viewmodel.VideoViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideosFragment : Fragment() {
    private lateinit var fragmentVideosBinding: FragmentVideosBinding
    private lateinit var videoFragmentRecyclerViewAdapter: VideoFragmentRecyclerViewAdapter
    private lateinit var videoFragmentRecyclerViewLayoutManager: LinearLayoutManager
    private var mainActivity: MainActivity? = null
    private val videoViewModel: VideoViewModel by activityViewModels()
    private val dataToTransferViewModel: DataToTransferViewModel by activityViewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.let {
            mainActivity = it as MainActivity
        }

        videoFragmentRecyclerViewAdapter = VideoFragmentRecyclerViewAdapter(
            DataToTransferRecyclerViewItemClickListener {
                if (dataToTransferViewModel.collectionOfDataToTransfer.contains(it)) {
                    mainActivity?.removeFromDataToTransferList(it)
                } else {
                    mainActivity?.addToDataToTransferList(it)
                }
            },
            dataToTransferViewModel.collectionOfDataToTransfer
        )
        videoFragmentRecyclerViewLayoutManager = LinearLayoutManager(requireContext())
        observeVideoViewModelLiveData()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        fragmentVideosBinding = FragmentVideosBinding.inflate(inflater, container, false)
        return fragmentVideosBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentVideosBinding.run {
            fragmentVideosRecyclerview.run {
                adapter = videoFragmentRecyclerViewAdapter
                layoutManager = videoFragmentRecyclerViewLayoutManager
                addItemDecoration(
                    HalfLineRecyclerViewCustomDivider(
                        requireContext(),
                        DividerItemDecoration.VERTICAL
                    )
                )
            }
        }
    }

    private fun observeVideoViewModelLiveData() {
        videoViewModel.allVideosOnDevice.observe(this) {
            videoFragmentRecyclerViewAdapter.submitList(it)
        }
        dataToTransferViewModel.sentDataButtonClicked.observe(this) {
            it.getEvent(javaClass.name)?.let {
                videoFragmentRecyclerViewAdapter.selectedVideos =
                    dataToTransferViewModel.collectionOfDataToTransfer
                videoFragmentRecyclerViewAdapter.notifyItemRangeChanged(
                    videoFragmentRecyclerViewLayoutManager.findFirstVisibleItemPosition(),
                    videoFragmentRecyclerViewLayoutManager.findLastVisibleItemPosition()
                )
            }
        }
    }
}