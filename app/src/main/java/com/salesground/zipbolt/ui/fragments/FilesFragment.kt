package com.salesground.zipbolt.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.salesground.zipbolt.MainActivity
import com.salesground.zipbolt.R
import com.salesground.zipbolt.databinding.FragmentFilesBinding
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.ui.recyclerview.DataToTransferRecyclerViewItemClickListener
import com.salesground.zipbolt.ui.recyclerview.directoryListDisplayFragment.DirectoryListDisplayRecyclerViewAdapter
import com.salesground.zipbolt.ui.recyclerview.directoryListDisplayFragment.DirectoryNavigationListAdapter
import com.salesground.zipbolt.viewmodel.FileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.IllegalStateException
import java.util.*
import kotlin.concurrent.schedule


@AndroidEntryPoint
class FilesFragment : Fragment() {
    private lateinit var directoryListDisplayRecyclerViewAdapter: DirectoryListDisplayRecyclerViewAdapter
    private lateinit var fragmentFilesBinding: FragmentFilesBinding
    private lateinit var recyclerViewLayoutManager: LinearLayoutManager
    private lateinit var directoryNavigationRecyclerViewLayoutMananger: LinearLayoutManager
    private lateinit var directoryNavigationListAdapter: DirectoryNavigationListAdapter

    companion object {
        var backStackCount: Int = 0
    }

    private val fileViewModel: FileViewModel by activityViewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.let {
            it as MainActivity
            it.setBackButtonPressedClickListener(object :
                MainActivity.PopBackStackListener {
                override fun popStack(): Boolean {
                    backStackCount--
                    fileViewModel.moveToPreviousDirectory()
                    return true
                }
            })
        }

        directoryListDisplayRecyclerViewAdapter =
            DirectoryListDisplayRecyclerViewAdapter(requireContext(),
                DataToTransferRecyclerViewItemClickListener {
                    it as DataToTransfer.DeviceFile
                    if (it.file.isDirectory) {
                        backStackCount++
                        fileViewModel.clearCurrentFolderChildren()
                        fileViewModel.moveToDirectory(
                            it.file.path
                        )
                    }
                })
        directoryNavigationListAdapter = DirectoryNavigationListAdapter(
            DataToTransferRecyclerViewItemClickListener {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            })
        directoryNavigationRecyclerViewLayoutMananger =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerViewLayoutManager = LinearLayoutManager(requireContext())
        observeViewModelLiveData()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        fragmentFilesBinding = FragmentFilesBinding.inflate(inflater, container, false)
        return fragmentFilesBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentFilesBinding.run {
            filesFragmentRecyclerView.run {
                layoutManager = recyclerViewLayoutManager
                adapter = directoryListDisplayRecyclerViewAdapter
            }
            filesFragmentDirectoryNavigationHeader.text = fileViewModel.navigationHeaderText
            /*  filesFragmentDirectoryNavigationRecyclerview.run {
                  adapter = directoryNavigationListAdapter
                  layoutManager = directoryNavigationRecyclerViewLayoutMananger
              }*/
        }
    }


    private fun observeViewModelLiveData() {
        fileViewModel.directoryChildren.observe(this) {
            it?.let {
                directoryListDisplayRecyclerViewAdapter.submitList(it)
            }
        }
        fileViewModel.navigatedDirectory.observe(this) {
            it?.let {
                fragmentFilesBinding.run {
                    filesFragmentDirectoryNavigationHeader.run {
                        var currentHeaderText = fileViewModel.navigationHeaderText
                        when (it.second) {
                            FileViewModel.ADDED_DIRECTORY -> {
                                fileViewModel.navigationHeaderText = getString(
                                    R.string.directory_navigation_header_text,
                                    currentHeaderText, it.first
                                )
                                text = fileViewModel.navigationHeaderText
                                fragmentFilesNavigationHeaderScrollView.post {
                                    fragmentFilesNavigationHeaderScrollView.smoothScrollTo(
                                        fragmentFilesNavigationHeaderScrollView
                                            .getChildAt(0).width, 0
                                    )
                                }
                            }
                            else -> {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    currentHeaderText =
                                        currentHeaderText.removeRange(
                                            currentHeaderText.length - (it.first.length + 3),
                                            currentHeaderText.length
                                        )
                                    withContext(Dispatchers.Main) {
                                        fileViewModel.navigationHeaderText = currentHeaderText
                                        text = currentHeaderText
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}