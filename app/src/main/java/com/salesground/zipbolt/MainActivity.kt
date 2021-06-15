package com.salesground.zipbolt


import android.Manifest.*
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.View.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.salesground.zipbolt.broadcast.DataTransferServiceConnectionStateReceiver
import com.salesground.zipbolt.broadcast.SendDataBroadcastReceiver
import com.salesground.zipbolt.broadcast.WifiDirectBroadcastReceiver
import com.salesground.zipbolt.broadcast.WifiDirectBroadcastReceiver.WifiDirectBroadcastReceiverCallback
import com.salesground.zipbolt.databinding.*
import com.salesground.zipbolt.databinding.ActivityMainBinding.inflate
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.model.ui.DiscoveredPeersDataItem
import com.salesground.zipbolt.model.ui.OngoingDataTransferUIState
import com.salesground.zipbolt.model.ui.PeerConnectionUIState
import com.salesground.zipbolt.notification.FileTransferServiceNotification
import com.salesground.zipbolt.service.DataTransferService
import com.salesground.zipbolt.ui.recyclerview.expandedsearchingforpeersinformation.DiscoveredPeersRecyclerViewAdapter
import com.salesground.zipbolt.ui.AllMediaOnDeviceViewPager2Adapter
import com.salesground.zipbolt.ui.recyclerview.ongoingDataTransferRecyclerViewComponents.OngoingDataTransferRecyclerViewAdapter
import com.salesground.zipbolt.ui.recyclerview.ongoingDataTransferRecyclerViewComponents.OngoingDataTransferRecyclerViewAdapter.*
import com.salesground.zipbolt.utils.customizeDate
import com.salesground.zipbolt.utils.parseDate
import com.salesground.zipbolt.utils.transformDataSizeToMeasuredUnit
import com.salesground.zipbolt.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.core.app.ActivityCompat.startActivityForResult

import android.content.Intent
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts


private const val FINE_LOCATION_REQUEST_CODE = 100
const val OPEN_MAIN_ACTIVITY_PENDING_INTENT_REQUEST_CODE = 1010

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainActivityViewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var ftsNotification: FileTransferServiceNotification

    @Inject
    lateinit var wifiP2pManager: WifiP2pManager

    @Inject
    lateinit var wifiManager: WifiManager

    @Inject
    lateinit var localBroadcastManager: LocalBroadcastManager

    private val sendDataClickedIntent =
        Intent(SendDataBroadcastReceiver.ACTION_SEND_DATA_BUTTON_CLICKED)

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    private lateinit var wifiDirectBroadcastReceiver: WifiDirectBroadcastReceiver
    private var dataTransferServiceIntent: Intent? = null

    private val turnOnWifiResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // create wifi direct group only if this device wants to be a sender
                createWifiDirectGroup()
            }
        }

    private val dataTransferServiceConnectionStateReceiver =
        DataTransferServiceConnectionStateReceiver(object :
            DataTransferServiceConnectionStateReceiver.ConnectionStateListener {
            override fun disconnectedFromPeer() {
                mainActivityViewModel.peerConnectionNoAction()
            }

            override fun cannotConnectToPeerAddress() {
                mainActivityViewModel.peerConnectionNoAction()
            }

            override fun connectionBroken() {

            }
        })

    private val dataTransferServiceDataReceiveListener: DataTransferService.DataFlowListener by lazy {
        object : DataTransferService.DataFlowListener {
            override fun onDataReceive(
                dataDisplayName: String,
                dataSize: Long,
                percentageOfDataRead: Float,
                dataType: Int,
                dataUri: Uri?,
                dataTransferStatus: DataToTransfer.TransferStatus
            ) {
                lifecycleScope.launch(Dispatchers.Main) {
                    when (dataTransferStatus) {
                        DataToTransfer.TransferStatus.RECEIVE_STARTED -> {
                            when (dataType) {
                                DataToTransfer.MediaType.IMAGE.value -> {
                                    mainActivityViewModel.expandedConnectedToPeerReceiveOngoing()
                                    with(
                                        connectedToPeerTransferOngoingBottomSheetLayoutBinding
                                            .expandedConnectedToPeerTransferOngoingLayout
                                            .expandedConnectedToPeerTransferOngoingLayoutHeader
                                    ) {
                                        // hide the  no item in receive label
                                        ongoingTransferReceiveHeaderLayoutNoItemsInReceiveTextView.root.animate()
                                            .alpha(0f)
                                        with(ongoingTransferReceiveHeaderLayoutDataReceiveView) {
                                            // ongoingDataTransferLayoutCancelTransferImageView.animate().alpha(1f)
                                            root.animate().alpha(1f)
                                            this.dataDisplayName = dataDisplayName
                                            this.dataSize =
                                                dataSize.transformDataSizeToMeasuredUnit(
                                                    ((percentageOfDataRead / 100) * dataSize).roundToLong()
                                                )
                                            Glide.with(ongoingDataReceiveLayoutImageView)
                                                .load(R.drawable.ic_startup_outline_)
                                                .into(ongoingDataReceiveLayoutImageView)
                                            // start shimmer
                                            ongoingDataReceiveDataCategoryImageShimmer.showShimmer(
                                                true
                                            )

                                        }
                                    }
                                }
                                else -> {


                                }
                            }
                        }

                        DataToTransfer.TransferStatus.RECEIVE_ONGOING -> {
                            with(
                                connectedToPeerTransferOngoingBottomSheetLayoutBinding
                                    .expandedConnectedToPeerTransferOngoingLayout
                                    .expandedConnectedToPeerTransferOngoingLayoutHeader
                                    .ongoingTransferReceiveHeaderLayoutDataReceiveView
                            ) {
                                // show the receive progress indicator and the percentage received
                                dataTransferPercent = percentageOfDataRead.roundToInt()
                                dataTransferPercentAsString = "$dataTransferPercent%"
                            }
                        }

                        DataToTransfer.TransferStatus.RECEIVE_COMPLETE -> {
                            lifecycleScope.launch(Dispatchers.Main) {
                                with(
                                    connectedToPeerTransferOngoingBottomSheetLayoutBinding
                                        .expandedConnectedToPeerTransferOngoingLayout
                                        .expandedConnectedToPeerTransferOngoingLayoutHeader
                                        .ongoingTransferReceiveHeaderLayoutDataReceiveView
                                ) {
                                    // show the media thumbnail at the end of the transfer
                                    dataTransferPercent = 100
                                    dataTransferPercentAsString = "$dataTransferPercent%"
                                    /*// hide the cancel transfer/receive image button
                            ongoingDataTransferLayoutCancelTransferImageView.animate().alpha(0f)*/
                                    // load the receive image into the image view
                                    Glide.with(ongoingDataReceiveLayoutImageView)
                                        .load(dataUri)
                                        .into(ongoingDataReceiveLayoutImageView)
                                    // stop shimmer
                                    ongoingDataReceiveDataCategoryImageShimmer.stopShimmer()
                                    ongoingDataReceiveDataCategoryImageShimmer.hideShimmer()
                                }
                                when (dataType) {
                                    DataToTransfer.MediaType.IMAGE.value -> {
                                        with(mainActivityViewModel) {
                                            addDataToCurrentTransferHistory(
                                                OngoingDataTransferUIState.DataItem(
                                                    DataToTransfer.DeviceImage(
                                                        0L,
                                                        dataUri!!,
                                                        System.currentTimeMillis().parseDate()
                                                            .customizeDate(),
                                                        dataDisplayName,
                                                        "",
                                                        dataSize,
                                                        ""
                                                    ).apply {
                                                        this.transferStatus =
                                                            DataToTransfer.TransferStatus.RECEIVE_COMPLETE
                                                    }
                                                )
                                            )
                                            ongoingDataTransferRecyclerViewAdapter.submitList(
                                                currentTransferHistory
                                            )
                                            ongoingDataTransferRecyclerViewAdapter.notifyItemInserted(
                                                currentTransferHistory.size - 1
                                            )
                                        }
                                    }
                                    else -> {
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun totalFileReceiveComplete() {
                mainActivityViewModel.totalFileReceiveComplete()
            }

            override fun onDataTransfer(
                dataToTransfer: DataToTransfer,
                percentTransferred: Float,
                transferStatus: DataToTransfer.TransferStatus
            ) {

                when (transferStatus) {
                    DataToTransfer.TransferStatus.TRANSFER_STARTED -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            with(
                                connectedToPeerTransferOngoingBottomSheetLayoutBinding
                                    .expandedConnectedToPeerTransferOngoingLayout
                                    .expandedConnectedToPeerTransferOngoingLayoutHeader
                            ) {
                                ongoingTransferReceiveHeaderLayoutNoItemsInTransferTextView.root.animate()
                                    .alpha(0f)
                                with(ongoingTransferReceiveHeaderLayoutDataTransferView) {
                                    dataSize =
                                        dataToTransfer.dataSize.transformDataSizeToMeasuredUnit(
                                            0L
                                        )

                                    dataDisplayName = dataToTransfer.dataDisplayName
                                    if (dataToTransfer.dataType == DataToTransfer.MediaType.IMAGE.value ||
                                        dataToTransfer.dataType == DataToTransfer.MediaType.VIDEO.value
                                    ) {
                                        Glide.with(ongoingDataTransferDataCategoryImageView)
                                            .load(dataToTransfer.dataUri)
                                            .into(ongoingDataTransferDataCategoryImageView)
                                    } else if (dataToTransfer.dataType == DataToTransfer.MediaType.APP.value) {
                                        dataToTransfer as DataToTransfer.DeviceApplication
                                        Glide.with(ongoingDataTransferDataCategoryImageView)
                                            .load(
                                                dataToTransfer.applicationInfo.loadIcon(
                                                    packageManager
                                                )
                                            )
                                            .into(ongoingDataTransferDataCategoryImageView)
                                    } else {

                                    }
                                }
                            }
                        }
                    }
                    DataToTransfer.TransferStatus.TRANSFER_COMPLETE -> {
                        lifecycleScope.launch {
                            with(
                                connectedToPeerTransferOngoingBottomSheetLayoutBinding
                                    .expandedConnectedToPeerTransferOngoingLayout
                                    .expandedConnectedToPeerTransferOngoingLayoutHeader
                                    .ongoingTransferReceiveHeaderLayoutDataTransferView
                            ) {
                                dataSize =
                                    dataToTransfer.dataSize.transformDataSizeToMeasuredUnit((dataToTransfer.dataSize))
                            }

                            mainActivityViewModel.currentTransferHistory.find {
                                it.id == dataToTransfer.dataUri.toString()
                            }.also {
                                it?.let { ongoingDataTransferUIState ->
                                    val index =
                                        mainActivityViewModel.currentTransferHistory.indexOf(
                                            ongoingDataTransferUIState
                                        )
                                    ongoingDataTransferUIState as OngoingDataTransferUIState.DataItem
                                    ongoingDataTransferUIState.dataToTransfer.transferStatus =
                                        transferStatus
                                    withContext(Dispatchers.Main) {
                                        ongoingDataTransferRecyclerViewAdapter.submitList(
                                            mainActivityViewModel.currentTransferHistory
                                        )
                                        ongoingDataTransferRecyclerViewAdapter.notifyItemChanged(
                                            index
                                        )
                                    }
                                }
                            }
                        }
                    }
                    DataToTransfer.TransferStatus.TRANSFER_ONGOING -> {
                        // update the transfer section of the UI
                        lifecycleScope.launch(Dispatchers.Main) {
                            with(
                                connectedToPeerTransferOngoingBottomSheetLayoutBinding
                                    .expandedConnectedToPeerTransferOngoingLayout
                                    .expandedConnectedToPeerTransferOngoingLayoutHeader
                            ) {

                                with(ongoingTransferReceiveHeaderLayoutDataTransferView) {
                                    dataSize =
                                        dataToTransfer.dataSize.transformDataSizeToMeasuredUnit(
                                            ((percentTransferred / 100) * dataToTransfer.dataSize).roundToLong()
                                        )
                                    dataTransferPercentAsString =
                                        "${percentTransferred.roundToInt()}%"
                                    dataTransferPercent = percentTransferred.roundToInt()
                                }
                            }
                        }
                    }
                    DataToTransfer.TransferStatus.TRANSFER_CANCELLED -> {
                        // from the cancelled media item from the queue of data in transfer
                        lifecycleScope.launch {
                            mainActivityViewModel.currentTransferHistory.find {
                                it.id == dataToTransfer.dataUri.toString()
                            }?.also { ongoingDataTransferUIState ->
                                val index =
                                    mainActivityViewModel.currentTransferHistory.indexOf(
                                        ongoingDataTransferUIState
                                    )
                                mainActivityViewModel.currentTransferHistory.removeAt(
                                    index
                                )
                                withContext(Dispatchers.Main) {
                                    ongoingDataTransferRecyclerViewAdapter.submitList(
                                        mainActivityViewModel.currentTransferHistory
                                    )
                                    ongoingDataTransferRecyclerViewAdapter.notifyItemRemoved(
                                        index
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ui variables
    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var modalBottomSheetDialog: BottomSheetDialog
    private var isSearchingForPeersBottomSheetLayoutConfigured: Boolean = false
    private var isConnectedToPeerNoActionBottomSheetLayoutConfigured: Boolean = false
    private var isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured: Boolean = false
    private var shouldStopPeerDiscovery: Boolean = false
    private var startPeerDiscovery: Boolean = false

    private val discoveredPeersRecyclerViewAdapter: DiscoveredPeersRecyclerViewAdapter by lazy {
        DiscoveredPeersRecyclerViewAdapter(
            connectToDeviceClickListener = object :
                DiscoveredPeersRecyclerViewAdapter.ConnectToDeviceClickListener {
                override fun onConnectToDevice(wifiP2pDevice: WifiP2pDevice) {
                    connectToADevice(wifiP2pDevice)
                    startPeerDiscovery = false
                    //   stopDevicePeerDiscovery()
                    /**
                     * 1. Collapse the searching for peers expanded bottom sheet ui
                     * 2. Display the connected to peer collapsed bottom sheet ui
                     * 3. Stop peer discovery
                     * **/
                }
            }
        )
    }

    private val ongoingDataTransferRecyclerViewAdapter = OngoingDataTransferRecyclerViewAdapter()

    private val expandedSearchingForPeersInfoBinding:
            ExpandedSearchingForPeersInformationBinding by lazy {
        MainActivityDataBindingUtils.getExpandedSearchingForPeersBinding(this)
    }

    private val collapsedSearchingForPeersInfoBinding:
            CollapsedSearchingForPeersInformationBinding by lazy {
        MainActivityDataBindingUtils.getCollapsedSearchingForPeersBinding(this)
    }

    private val connectedToPeerNoActionBottomSheetLayoutBinding:
            ConnectedToPeerNoActionPersistentBottomSheetLayoutBinding by lazy {
        MainActivityDataBindingUtils.getConnectedToPeerNoActionPersistentBottomSheetBinding(this)
    }

    private val connectedToPeerTransferOngoingBottomSheetLayoutBinding:
            ConnectedToPeerTransferOngoingPersistentBottomSheetBinding by lazy {
        MainActivityDataBindingUtils.getConnectedToPeerTransferOngoingPersistentBottomSheetBinding(
            this
        )
    }

    // persistent bottom sheet behavior variables
    private val searchingForPeersBottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(
            activityMainBinding.connectionInfoPersistentBottomSheetLayout.root
        )
    }

    private val connectedToPeerNoActionBottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(
            connectedToPeerNoActionBottomSheetLayoutBinding.root
        )
    }

    private val connectedToPeerTransferOngoingBottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(
            connectedToPeerTransferOngoingBottomSheetLayoutBinding.root
        )
    }


    // service variables
    private var dataTransferService: DataTransferService? = null
    private val dataTransferServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            service as DataTransferService.DataTransferServiceBinder
            service.getServiceInstance()
                .setOnDataReceiveListener(dataTransferServiceDataReceiveListener)
            dataTransferService = service.getServiceInstance()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dataTransferService = null
        }
    }

    private val wifiDirectBroadcastReceiverCallback = object : WifiDirectBroadcastReceiverCallback {
        override fun wifiOn() {

        }

        override fun wifiOff() {

        }

        override fun peersListAvailable(peersList: MutableList<WifiP2pDevice>) {
            mainActivityViewModel.peersListAvailable(peersList)
        }

        override fun connectedToPeer(
            wifiP2pInfo: WifiP2pInfo,
            peeredDevice: WifiP2pDevice
        ) {
            startPeerDiscovery = false
            // update the ui to show that this device is connected to peer
            mainActivityViewModel.connectedToPeer(wifiP2pInfo, peeredDevice)
            if (dataTransferService?.isActive == true) {

            } else {
                // start data transfer service
                when (wifiP2pInfo.isGroupOwner) {
                    true -> {
                        // you are the server
                        Intent(
                            this@MainActivity,
                            DataTransferService::class.java
                        ).also { serviceIntent ->

                            dataTransferServiceIntent = serviceIntent.apply {
                                putExtra(DataTransferService.IS_SERVER, true)
                                putExtra(DataTransferService.IS_ONE_DIRECTIONAL_TRANSFER, true)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                            bindService(
                                serviceIntent,
                                dataTransferServiceConnection,
                                BIND_AUTO_CREATE
                            )
                        }
                    }
                    false -> {
                        // you are the client
                        Intent(
                            this@MainActivity,
                            DataTransferService::class.java
                        ).also { serviceIntent ->
                            val serverIpAddress =
                                wifiP2pInfo.groupOwnerAddress.hostAddress
                            dataTransferServiceIntent = serviceIntent.apply {
                                putExtra(DataTransferService.IS_SERVER, false)
                                putExtra(DataTransferService.SERVER_IP_ADDRESS, serverIpAddress)
                                putExtra(DataTransferService.IS_ONE_DIRECTIONAL_TRANSFER, true)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                            bindService(
                                serviceIntent,
                                dataTransferServiceConnection,
                                BIND_AUTO_CREATE
                            )
                        }
                    }
                }
            }
        }

        override fun wifiP2pDiscoveryStopped() {
            // in order to avoid disrupting the ui state due to multiple broadcast events
            // make sure you end the peer discovery only when the user specifies so
            if (shouldStopPeerDiscovery) {
                mainActivityViewModel.peerConnectionNoAction()
            } else {
                if (startPeerDiscovery) {
                    beginPeerDiscovery()
                }
            }
        }

        override fun wifiP2pDiscoveryStarted() {
            // only inform the view model that the device has began searching
            // for peers when there is no ui action
            if (mainActivityViewModel.peerConnectionUIState.value ==
                PeerConnectionUIState.NoConnectionUIAction
            ) {
                mainActivityViewModel.expandedSearchingForPeers()
            }
        }


        override fun disconnectedFromPeer() {
            mainActivityViewModel.peerConnectionNoAction()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inflate(layoutInflater).apply {
            activityMainBinding = this
            connectToPeerButton.setOnClickListener {
                if (it.alpha > 0f) {
                    configureConnectionOptionsModalBottomSheetLayout()
                    modalBottomSheetDialog.show()
                }
            }

            sendFileButton.setOnClickListener {
                if (it.alpha != 0f) {
                    // send broadcast event that send data button has been triggered
                    localBroadcastManager.sendBroadcast(sendDataClickedIntent)

                    mainActivityViewModel.addCurrentDataToTransferToUIState()
                    mainActivityViewModel.expandedConnectedToPeerTransferOngoing()
                    with(
                        connectedToPeerTransferOngoingBottomSheetLayoutBinding
                            .expandedConnectedToPeerTransferOngoingLayout
                            .expandedConnectedToPeerTransferOngoingLayoutHeader
                    ) {
                        ongoingTransferReceiveHeaderLayoutNoItemsInTransferTextView.root.animate()
                            .alpha(0f)
                        ongoingTransferReceiveHeaderLayoutDataTransferView.root.animate().alpha(1f)

                    }
                    connectedToPeerTransferOngoingBottomSheetLayoutBinding
                        .expandedConnectedToPeerTransferOngoingLayout
                        .expandedConnectedToPeerTransferOngoingLayoutHeader
                        .apply {
                            ongoingTransferReceiveHeaderLayoutNoItemsInTransferTextView.root.animate()
                                .alpha(0f)
                            ongoingTransferReceiveHeaderLayoutDataTransferView.root.animate()
                                .alpha(1f)
                        }


                    // transfer data using the DataTransferService
                    dataTransferService?.transferData(
                        mainActivityViewModel.collectionOfDataToTransfer
                    )
                    // clear collection of data to transfer since transfer has been completed
                    mainActivityViewModel.clearCollectionOfDataToTransfer()
                }
            }

            with(mainActivityAllMediaOnDevice) {
                // change the tab mode based on the current screen density
                allMediaOnDeviceTabLayout.tabMode = if (resources.configuration.fontScale > 1.1) {
                    TabLayout.MODE_SCROLLABLE
                } else TabLayout.MODE_FIXED

                allMediaOnDeviceViewPager.adapter = AllMediaOnDeviceViewPager2Adapter(
                    supportFragmentManager,
                    lifecycle
                )
                TabLayoutMediator(
                    allMediaOnDeviceTabLayout,
                    allMediaOnDeviceViewPager
                ) { tab, position ->
                    when (position) {
                        0 -> tab.text = "Apps"
                        1 -> tab.text = "Images"
                        2 -> tab.text = "Videos"
                        3 -> tab.text = "Music"
                        4 -> tab.text = "Files"
                    }
                }.attach()
            }
            setContentView(root)
        }
        lifecycle.apply {
            addObserver(ftsNotification)
        }
        observeViewModelLiveData()
        initializeChannelAndBroadcastReceiver()
        // bind to the data transfer service
        Intent(this, DataTransferService::class.java).also {
            bindService(it, dataTransferServiceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun observeViewModelLiveData() {
        mainActivityViewModel.peerConnectionUIState.observe(this) {
            it?.let {
                when (it) {
                    is PeerConnectionUIState.CollapsedConnectedToPeerTransferOngoing -> {
                        if (!isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured) {
                            configureConnectedToPeerTransferOngoingBottomSheetLayout()
                        }
                        connectedToPeerTransferOngoingBottomSheetBehavior.apply {
                            state = BottomSheetBehavior.STATE_COLLAPSED
                            peekHeight = getBottomSheetPeekHeight()
                        }

                        // hide the connected to pair no action bottom sheet
                        connectedToPeerNoActionBottomSheetBehavior.apply {
                            isHideable = true
                            state = BottomSheetBehavior.STATE_HIDDEN
                        }
                        connectedToPeerNoActionBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_HIDDEN
                    }
                    is PeerConnectionUIState.CollapsedSearchingForPeer -> {
                        // update the UI to display the number of devices found
                        if (!isSearchingForPeersBottomSheetLayoutConfigured) {
                            configureSearchingForPeersPersistentBottomSheetInfo()
                            expandedSearchingForPeersInfoBinding.root.alpha = 0f
                        }
                        collapsedSearchingForPeersInfoBinding.numberOfDevicesFound =
                            it.numberOfDevicesFound
                        collapseSearchingForPeersBottomSheet()
                    }
                    is PeerConnectionUIState.ExpandedConnectedToPeerTransferOngoing -> {
                        // Log.i("ReceivingInfo", "New file received UI update")
                        if (!isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured) {
                            configureConnectedToPeerTransferOngoingBottomSheetLayout()
                        }

                        // submit the list of items in transfer queue to the adapter
                        ongoingDataTransferRecyclerViewAdapter.submitList(it.collectionOfDataToTransfer)
                        ongoingDataTransferRecyclerViewAdapter.notifyDataSetChanged()


                        with(connectedToPeerTransferOngoingBottomSheetBehavior) {
                            state =
                                BottomSheetBehavior.STATE_EXPANDED
                            peekHeight =
                                getBottomSheetPeekHeight()
                        }
                        // hide the connected to pair no action bottom sheet
                        with(connectedToPeerNoActionBottomSheetBehavior) {
                            isHideable = true
                            state = BottomSheetBehavior.STATE_HIDDEN
                        }
                    }
                    is PeerConnectionUIState.ExpandedSearchingForPeer -> {
                        if (!isSearchingForPeersBottomSheetLayoutConfigured) {
                            configureSearchingForPeersPersistentBottomSheetInfo()
                            collapsedSearchingForPeersInfoBinding.root.alpha = 0f
                        }
                        discoveredPeersRecyclerViewAdapter.submitList(it.devices.map { wifiP2pDevice ->
                            DiscoveredPeersDataItem.DiscoveredPeer(wifiP2pDevice)
                        }.toMutableList())
                        searchingForPeersBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_EXPANDED
                        searchingForPeersBottomSheetBehavior.peekHeight = getBottomSheetPeekHeight()
                    }
                    PeerConnectionUIState.NoConnectionUIAction -> {
                        if (isConnectedToPeerNoActionBottomSheetLayoutConfigured) {
                            connectedToPeerNoActionBottomSheetBehavior.isHideable = true
                            connectedToPeerNoActionBottomSheetBehavior.state =
                                BottomSheetBehavior.STATE_HIDDEN
                            isConnectedToPeerNoActionBottomSheetLayoutConfigured = false
                        }
                        if (isSearchingForPeersBottomSheetLayoutConfigured) {
                            searchingForPeersBottomSheetBehavior.isHideable = true
                            searchingForPeersBottomSheetBehavior.state =
                                BottomSheetBehavior.STATE_HIDDEN
                            isSearchingForPeersBottomSheetLayoutConfigured = false
                        }
                        if (isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured) {
                            connectedToPeerTransferOngoingBottomSheetBehavior.isHideable = true
                            connectedToPeerTransferOngoingBottomSheetBehavior.state =
                                BottomSheetBehavior.STATE_HIDDEN
                            isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured = false
                        }
                        with(activityMainBinding) {
                            sendFileButton.animate().alpha(0f)
                        }
                    }
                    is PeerConnectionUIState.CollapsedConnectedToPeerNoAction -> {
                        // in case of a configuration or theme change, inflate and configure the bottom sheet
                        if (!isConnectedToPeerNoActionBottomSheetLayoutConfigured) {
                            configureConnectedToPeerNoActionBottomSheetLayoutInfo(
                                it.connectedDevice
                            )
                        }
                        // hide the searching for peers bottom
                        searchingForPeersBottomSheetBehavior.isHideable = true
                        searchingForPeersBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_HIDDEN

                        // show the send button
                        activityMainBinding.sendFileButton.animate().alpha(1f)

                        // stop searching for peers animation
                        expandedSearchingForPeersInfoBinding
                            .expandedSearchingForPeersInformationSearchingForDevicesAnimation
                            .setKeepAnimating(false)
                        collapsedSearchingForPeersInfoBinding
                            .mediumSearchingForPeersAnimation
                            .setKeepAnimating(false)

                        // hide the expanded connected to pair no action layout
                        connectedToPeerNoActionBottomSheetLayoutBinding
                            .expandedConnectedToPeerNoActionLayout
                            .root
                            .alpha = 0f

                        // set bottom sheet peek height
                        connectedToPeerNoActionBottomSheetBehavior.peekHeight =
                            getBottomSheetPeekHeight()
                        connectedToPeerNoActionBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_COLLAPSED
                    }

                    is PeerConnectionUIState.ExpandedConnectedToPeerNoAction -> {
                        if (!isConnectedToPeerNoActionBottomSheetLayoutConfigured) configureConnectedToPeerNoActionBottomSheetLayoutInfo(
                            it.connectedDevice
                        )
                        // hide the searching for peers bottom
                        searchingForPeersBottomSheetBehavior.isHideable = true
                        searchingForPeersBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_HIDDEN

                        connectedToPeerNoActionBottomSheetLayoutBinding
                            .collapsedConnectedToPeerNoActionLayout
                            .root
                            .alpha = 0f
                        connectedToPeerNoActionBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_EXPANDED
                    }
                }
            }
        }
    }

    private fun configureConnectedToPeerTransferOngoingBottomSheetLayout() {
        isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured = true
        with(connectedToPeerTransferOngoingBottomSheetLayoutBinding) {
            // configure collapsed connected to peer transfer ongoing layout
            with(collapsedConnectedToPeerOngoingDataTransferLayout) {
                root.animate().alpha(0f)
            }

            // configure expanded connected to peer transfer ongoing layout
            with(expandedConnectedToPeerTransferOngoingLayout) {
                with(expandedConnectedToPeerTransferOngoingToolbar) {
                    this.expandedBottomSheetLayoutToolbarCancelButton.setOnClickListener {
                        // close the connection with the peer
                        dataTransferServiceIntent?.let {
                            unbindService(dataTransferServiceConnection)
                            stopService(dataTransferServiceIntent)
                        }
                    }
                    this.expandedBottomSheetLayoutToolbarCollapseBottomSheetButton.setOnClickListener {
                        // collapse the connected to peer transfer ongoing bottom sheet
                        connectedToPeerTransferOngoingBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_COLLAPSED
                        connectedToPeerTransferOngoingBottomSheetBehavior.peekHeight =
                            getBottomSheetPeekHeight()
                    }
                }
                with(expandedConnectedToPeerTransferOngoingRecyclerView) {
                    adapter = ongoingDataTransferRecyclerViewAdapter
                    val gridLayoutManager = GridLayoutManager(this@MainActivity, 3)
                    gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return when (ongoingDataTransferRecyclerViewAdapter.getItemViewType(
                                position
                            )) {
                                OngoingDataTransferAdapterViewTypes.IMAGE_TRANSFER_WAITING.value -> 1
                                OngoingDataTransferAdapterViewTypes.IMAGE_TRANSFER_OR_RECEIVE_COMPLETE.value -> 1
                                OngoingDataTransferAdapterViewTypes.CATEGORY_HEADER.value -> 3
                                else -> 3
                            }
                        }
                    }
                    layoutManager = gridLayoutManager
                    setHasFixedSize(true)
                }
                with(expandedConnectedToPeerTransferOngoingLayoutHeader) {
                    ongoingTransferReceiveHeaderLayoutDataTransferView.root.animate().alpha(0f)
                    ongoingTransferReceiveHeaderLayoutDataReceiveView.root.animate().alpha(0f)

                    with(ongoingTransferReceiveHeaderLayoutDataReceiveView) {
                        if (root.alpha != 0f) {
                            ongoingDataReceiveLayoutCancelTransferImageButton.setOnClickListener {
                                dataTransferService?.cancelActiveReceive()
                            }
                        }
                    }

                    with(ongoingTransferReceiveHeaderLayoutDataTransferView) {
                        if (root.alpha != 0f) {
                            ongoingDataTransferLayoutCancelTransferImageButton.setOnClickListener {
                                dataTransferService?.cancelActiveTransfer()
                            }
                        }
                    }
                }
            }
        }

        connectedToPeerTransferOngoingBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        mainActivityViewModel.expandedConnectedToPeerTransferOngoing()
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        mainActivityViewModel.collapsedConnectedToPeerTransferOngoing()
                    }
                    else -> {
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                connectedToPeerTransferOngoingBottomSheetLayoutBinding
                    .collapsedConnectedToPeerOngoingDataTransferLayout
                    .root.alpha = 1 - slideOffset * 3.5f
                connectedToPeerTransferOngoingBottomSheetLayoutBinding
                    .expandedConnectedToPeerTransferOngoingLayout
                    .root.alpha = slideOffset
            }
        })
    }

    private fun configureConnectedToPeerNoActionBottomSheetLayoutInfo(connectedDevice: WifiP2pDevice) {
        isConnectedToPeerNoActionBottomSheetLayoutConfigured = true
        connectedToPeerNoActionBottomSheetLayoutBinding.apply {
            collapsedConnectedToPeerNoActionLayout.apply {
                deviceConnectedTo = "Connected to ${connectedDevice.deviceName ?: "unknown device"}"
                collapsedConnectedToPeerNoTransferBreakConnectionButton.setOnClickListener {

                }
                collapsedConnectedToPeerNoTransferBreakConnectionButton.setOnClickListener {
                    cancelDeviceConnection()
                    // TODO, remove later
                    mainActivityViewModel.peerConnectionNoAction()
                }

                root.setOnClickListener {
                    mainActivityViewModel.expandedConnectedToPeerNoAction()
                }
            }
            expandedConnectedToPeerNoActionLayout.apply {
                deviceAddress = connectedDevice.deviceAddress
                deviceName = connectedDevice.deviceName
                collapseExpandedConnectedToPeerNoActionImageButton.setOnClickListener {
                    mainActivityViewModel.collapsedConnectedToPeerNoAction()
                }
                expandedConnectedToPeerNoActionCloseConnectionImageButton.setOnClickListener {
                    cancelDeviceConnection()
                    // TODO, remove later
                    mainActivityViewModel.peerConnectionNoAction()
                }
            }
        }
        connectedToPeerNoActionBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        mainActivityViewModel.expandedConnectedToPeerNoAction()
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        mainActivityViewModel.collapsedConnectedToPeerNoAction()
                    }
                    else -> {
                    }
                }
            }


            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                connectedToPeerNoActionBottomSheetLayoutBinding.collapsedConnectedToPeerNoActionLayout.root.alpha =
                    1 - slideOffset * 3.5f
                connectedToPeerNoActionBottomSheetLayoutBinding
                    .expandedConnectedToPeerNoActionLayout.root.alpha = slideOffset
            }
        })
    }


    private fun configureSearchingForPeersPersistentBottomSheetInfo() {
        isSearchingForPeersBottomSheetLayoutConfigured = true

        collapsedSearchingForPeersInfoBinding.apply {
            collapsedSearchingForPeersInformationCancelSearchingForPeers.setOnClickListener {
                stopDevicePeerDiscovery()
            }
            root.setOnClickListener {
                mainActivityViewModel.expandedSearchingForPeers()
            }
        }

        expandedSearchingForPeersInfoBinding.apply {
            collapseExpandedSearchingForPeersImageButton.setOnClickListener {
                mainActivityViewModel.collapsedSearchingForPeers()
            }
            expandedSearchingForPeersInformationStopSearchButton.setOnClickListener {
                stopDevicePeerDiscovery()
            }
            expandedSearchingForPeersInformationDiscoveredPeersRecyclerView.apply {
                adapter = discoveredPeersRecyclerViewAdapter
            }
        }
        searchingForPeersBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        mainActivityViewModel.collapsedSearchingForPeers()
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        mainActivityViewModel.expandedSearchingForPeers()
                    }
                    else -> {
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                collapsedSearchingForPeersInfoBinding.root.alpha = 1 - slideOffset * 3.5f
                expandedSearchingForPeersInfoBinding.root.alpha = slideOffset
            }
        })
    }

    private fun collapseSearchingForPeersBottomSheet() {
        searchingForPeersBottomSheetBehavior.peekHeight = getBottomSheetPeekHeight()
        searchingForPeersBottomSheetBehavior.state =
            BottomSheetBehavior.STATE_COLLAPSED
    }


    private fun configureConnectionOptionsModalBottomSheetLayout() {
        modalBottomSheetDialog = BottomSheetDialog(this)
        modalBottomSheetDialog.setContentView(
            ZipBoltProConnectionOptionsBottomSheetLayoutBinding.inflate(layoutInflater).apply {
                zipBoltProConnectionOptionsBottomSheetLayoutSendCardView.setOnClickListener {
                    // Turn on device wifi if it is off
                    if (!wifiManager.isWifiEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            turnOnWifiResultLauncher.launch(Intent(Settings.Panel.ACTION_WIFI))
                        } else {
                            wifiManager.isWifiEnabled = true
                        }
                    } else {
                        // Create Wifi p2p group, if wifi is enabled
                        //createWifiDirectGroup()
                        beginPeerDiscovery()
                    }
                    // TODO     2. Display waiting for peer screen and instructions for peer device to follow

                }
                zipBoltProConnectionOptionsBottomSheetLayoutReceiveCardView.setOnClickListener {
                    // Turn on device wifi
                    if (!wifiManager.isWifiEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            turnOnWifiResultLauncher.launch(Intent(Settings.Panel.ACTION_WIFI))
                        } else {
                            wifiManager.isWifiEnabled = true
                        }
                    } else {
                        // TODO 2. Turn on device location

                        // Create Wifi p2p group, if wifi is enabled
                        // begin peer discovery
                        beginPeerDiscovery()
                    }


                }

                zipBoltProConnectionOptionsBottomSheetLayoutSendAndReceiveCardView.setOnClickListener {

                }
            }.root
        )

    }

    private fun configurePlatformOptionsModalBottomSheetLayout() {
        modalBottomSheetDialog = BottomSheetDialog(this@MainActivity)
        val modalBottomSheetLayoutBinding =
            ZipBoltConnectionOptionsBottomSheetLayoutBinding.inflate(layoutInflater)

        modalBottomSheetLayoutBinding.apply {
            connectToAndroid.setOnClickListener {
                if (!isSearchingForPeersBottomSheetLayoutConfigured) configureSearchingForPeersPersistentBottomSheetInfo()
                activityMainBinding.apply {
                    connectionInfoPersistentBottomSheetLayout.apply {
                        modalBottomSheetDialog.dismiss()
                        beginPeerDiscovery()
                    }
                }
            }
            connectToIphone.setOnClickListener {
                displayToast("Connect to iPhone")
            }
            connectToDesktop.setOnClickListener {
                displayToast("Connect to Desktop")
            }
            modalBottomSheetDialog.setContentView(root)
        }
    }

    fun addToDataToTransferList(dataToTransfer: DataToTransfer) {
        mainActivityViewModel.addDataToTransfer(dataToTransfer)
    }

    fun removeFromDataToTransferList(dataToTransfer: DataToTransfer) {
        mainActivityViewModel.removeDataFromDataToTransfer(dataToTransfer)
    }


    private fun getBottomSheetPeekHeight(): Int {
        return (60 * resources.displayMetrics.density).roundToInt()
    }


    @SuppressLint("MissingPermission", "HardwareIds")
    private fun createWifiDirectGroup() {
        val wifiP2pConfig = WifiP2pConfig().apply {
            deviceAddress = wifiManager.connectionInfo.macAddress
            wps.setup = WpsInfo.DISPLAY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiP2pManager.createGroup(wifiP2pChannel, wifiP2pConfig,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        displayToast("Group created successfully")
                    }

                    override fun onFailure(p0: Int) {
                        displayToast("Group creation failed")
                    }
                })
        } else {
            wifiP2pManager.createGroup(wifiP2pChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        displayToast("Group created successfully")
                        wifiP2pManager.requestGroupInfo(wifiP2pChannel) {
                            it?.let {
                                Toast.makeText(
                                    this@MainActivity, "Password is " +
                                            it.passphrase, Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    override fun onFailure(p0: Int) {
                        displayToast("Group creation failed")
                    }
                })
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToADevice(device: WifiP2pDevice) {
        val wifiP2pConfig = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            // this device that initiates the connect shall be the group owner
            groupOwnerIntent = 0
        }

        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // Broadcast receiver notifies us in WIFI_P2P_CONNECTION_CHANGED_ACTION
                    displayToast("Connection attempt successful")
                }

                override fun onFailure(p0: Int) {
                    // connection initiation failed,
                    displayToast("Connection attempt failed")
                }
            })
    }


    private val nearByDevices = mutableMapOf<String, String>()

    @SuppressLint("MissingPermission")
    private fun beginPeerDiscovery() {
        if (isLocationPermissionGranted()) {
            val recordListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomainName: String?,
                                                                         txtRecordMap: MutableMap<String, String>?, srcDevice: WifiP2pDevice? ->
                if (txtRecordMap != null && srcDevice != null) {
                    txtRecordMap["peerName"]?.let { peerName ->
                        nearByDevices[srcDevice.deviceAddress] = peerName
                    }
                }
            }

            val serviceInfoListener =
                WifiP2pManager.DnsSdServiceResponseListener { instanceName: String?,
                                                              registrationType: String?,
                                                              srcDevice: WifiP2pDevice? ->
                    // replace the default device name, with the peer name sent through the service record
                    srcDevice?.let {
                        srcDevice.deviceName =
                            nearByDevices[srcDevice.deviceAddress] ?: srcDevice.deviceName
                        if(instanceName == getString(R.string.zip_bolt_file_transfer_service))
                        mainActivityViewModel.newDeviceAdvertisingZipBoltTransferService(
                            srcDevice
                        )
                    }
                }

            wifiP2pManager.setDnsSdResponseListeners(
                wifiP2pChannel,
                serviceInfoListener,
                recordListener
            )
            wifiP2pManager.addServiceRequest(wifiP2pChannel,
                WifiP2pDnsSdServiceRequest.newInstance(),
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {

                    }

                    override fun onFailure(reason: Int) {

                    }
                })

            wifiP2pManager.discoverServices(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {

                }

                override fun onFailure(reason: Int) {

                }
            })
            /*wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    startPeerDiscovery = true
                    // TODO Peer discovery started alert the user
                    //  displayToast("Peer discovery successfully initiated")

                }

                override fun onFailure(p0: Int) {
                    // TODO Peer discovery initiation failed, alert the user
                    displayToast("Peer discovery initiation failed")
                }

            })*/
        } else {
            checkFineLocationPermission()
        }
    }


    private fun cancelDeviceConnection() {
        wifiP2pManager.removeGroup(wifiP2pChannel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    displayToast("P2p connection canceled")
                }

                override fun onFailure(reason: Int) {
                    displayToast("Cannot disconnect from device")
                }
            })
    }

    private fun stopDevicePeerDiscovery() {
        if (isLocationPermissionGranted()) {
            wifiP2pManager.stopPeerDiscovery(
                wifiP2pChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        shouldStopPeerDiscovery = true
                    }

                    override fun onFailure(p0: Int) {
                        displayToast("Couldn't stop peer discovery")
                    }
                })
        }
    }

    private fun createSystemBroadcastIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
    }


    @SuppressLint("MissingPermission")
    private fun initializeChannelAndBroadcastReceiver() {
        wifiP2pChannel =
            wifiP2pManager.initialize(
                this, mainLooper
            ) {

            }
        // use the activity, wifiP2pManager and wifiP2pChannel to initialize the wifiDiectBroadcastReceiver
        wifiP2pChannel.also { channel: WifiP2pManager.Channel ->
            wifiDirectBroadcastReceiver = WifiDirectBroadcastReceiver(
                wifiDirectBroadcastReceiverCallback = wifiDirectBroadcastReceiverCallback,
                wifiP2pManager = wifiP2pManager,
                wifiP2pChannel = wifiP2pChannel
            )
        }

        // register the zipBolt file transfer service
        val record: Map<String, String> = mapOf(
            "peerName" to "P.C. Ekwerike"
        )

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            getString(R.string.zip_bolt_file_transfer_service),
            "_presence._tcp",
            record
        )

        if (isLocationPermissionGranted()) {
            wifiP2pManager.addLocalService(wifiP2pChannel, serviceInfo,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        // local service addition was successfully sent to the android framework
                    }

                    override fun onFailure(reason: Int) {
                        // local service addition was not successfully sent to the android framework
                    }
                })
        } else {
            // request location permission and addLocalService again
        }
    }

    // check if SpeedForce has access to device fine location
    private fun checkFineLocationPermission() {
        val isFineLocationPermissionGranted = isLocationPermissionGranted()

        if (isFineLocationPermissionGranted) {
            // TODO check if the device location is on, using location manager
            //TODO more resource @ https://developer.android.com/training/location
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission.ACCESS_FINE_LOCATION),
                FINE_LOCATION_REQUEST_CODE
            )
        }
    }

    private fun isLocationPermissionGranted() = ActivityCompat.checkSelfPermission(
        this,
        permission.ACCESS_FINE_LOCATION
    ) ==
            PackageManager.PERMISSION_GRANTED

    private fun displayToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        PermissionUtils.checkReadAndWriteExternalStoragePermission(this)
        registerReceiver(wifiDirectBroadcastReceiver, createSystemBroadcastIntentFilter())
        localBroadcastManager.registerReceiver(
            dataTransferServiceConnectionStateReceiver,
            IntentFilter().apply {
                addAction(DataTransferServiceConnectionStateReceiver.ACTION_DISCONNECTED_FROM_PEER)
                addAction(DataTransferServiceConnectionStateReceiver.ACTION_CANNOT_CONNECT_TO_PEER_ADDRESS)
            }
        )
    }

    override fun onStop() {
        super.onStop()
        unbindService(dataTransferServiceConnection)
        // unregister the broadcast receiver
        unregisterReceiver(wifiDirectBroadcastReceiver)
        localBroadcastManager.unregisterReceiver(dataTransferServiceConnectionStateReceiver)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == FINE_LOCATION_REQUEST_CODE && permissions.contains(permission.ACCESS_FINE_LOCATION)) {
            if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // access to device fine location has been granted to SpeedForce
                    // TODO check if the device location is on, using location manager
                    //TODO more resource @ https://developer.android.com/training/location
                }
            }
        } else if (requestCode == PermissionUtils.READ_WRITE_STORAGE_REQUEST_CODE && permissions.contains(
                permission.READ_EXTERNAL_STORAGE
            ) &&
            permissions.contains(permission.WRITE_EXTERNAL_STORAGE)
        ) {
            if (grantResults.isNotEmpty()) {
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    // SpeedForce has permission to read and write ot the device external storage
                    // TODO Alert the viewModel to go ahead and fetch media and files from the repositories
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}