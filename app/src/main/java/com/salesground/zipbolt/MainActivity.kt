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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.salesground.zipbolt.broadcast.DataTransferServiceConnectionStateReceiver
import com.salesground.zipbolt.broadcast.SendDataBroadcastReceiver
import com.salesground.zipbolt.broadcast.WifiDirectBroadcastReceiver.WifiDirectBroadcastReceiverCallback
import com.salesground.zipbolt.databinding.*
import com.salesground.zipbolt.databinding.ActivityMainBinding.inflate
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.model.ui.DiscoveredPeersDataItem
import com.salesground.zipbolt.model.ui.PeerConnectionUIState
import com.salesground.zipbolt.notification.FileTransferServiceNotification
import com.salesground.zipbolt.service.DataTransferService
import com.salesground.zipbolt.ui.recyclerview.expandedsearchingforpeersinformation.DiscoveredPeersRecyclerViewAdapter
import com.salesground.zipbolt.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayoutMediator
import com.salesground.zipbolt.broadcast.UpgradedWifiDirectBroadcastReceiver
import com.salesground.zipbolt.model.MediaType
import com.salesground.zipbolt.ui.AllMediaOnDeviceViewPagerAdapter
import com.salesground.zipbolt.ui.fragments.FilesFragment
import com.salesground.zipbolt.ui.fragments.GroupCreatedFragment
import com.salesground.zipbolt.ui.fragments.PeersDiscoveryFragment
import com.salesground.zipbolt.ui.recyclerview.SentAndReceiveDataItemsViewPagerAdapter
import com.salesground.zipbolt.utils.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.concurrent.schedule


private const val FINE_LOCATION_REQUEST_CODE = 100
const val OPEN_MAIN_ACTIVITY_PENDING_INTENT_REQUEST_CODE = 1010

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    interface PopBackStackListener {
        fun popStack(): Boolean
    }

    private var popBackStackListener: PopBackStackListener? = null

    fun setBackButtonPressedClickListener(popBackStackListener: PopBackStackListener) {
        this.popBackStackListener = popBackStackListener
    }

    enum class DeviceTransferRole(value: Int) {
        SEND(1),
        RECEIVE(2),
        SEND_AND_RECEIVE(3),
        SEND_AND_RECEIVE_BUT_DISCOVERING(7),
        SEND_BUT_DISCOVERING_PEER(5),
        RECEIVE_BUT_DISCOVERING_PEER(6),
        NO_ROLE(4)
    }

    private val mainActivityViewModel: MainActivityViewModel by viewModels()
    private val dataToTransferViewModel: DataToTransferViewModel by viewModels()
    private val sentDataViewModel: SentDataViewModel by viewModels()
    private val receivedDataViewModel: ReceivedDataViewModel by viewModels()

    //fragments
    private var groupCreatedFragment: GroupCreatedFragment? = null
    private var peersDiscoveryFragment: PeersDiscoveryFragment? = null

    @Inject
    lateinit var ftsNotification: FileTransferServiceNotification

    @Inject
    lateinit var wifiP2pManager: WifiP2pManager

    @Inject
    lateinit var wifiManager: WifiManager

    @Inject
    lateinit var localBroadcastManager: LocalBroadcastManager

    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val sendDataClickedIntent =
        Intent(SendDataBroadcastReceiver.ACTION_SEND_DATA_BUTTON_CLICKED)

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    private lateinit var upgradedWifiDirectBroadcastReceiver: UpgradedWifiDirectBroadcastReceiver
    private var dataTransferServiceIntent: Intent? = null
    private var deviceTransferRole: DeviceTransferRole = DeviceTransferRole.NO_ROLE
    private val turnOnWifiResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                when (deviceTransferRole) {
                    DeviceTransferRole.SEND_AND_RECEIVE_BUT_DISCOVERING -> {
                        openPeersDiscoveryModalBottomSheet()
                    }
                    DeviceTransferRole.SEND_BUT_DISCOVERING_PEER -> {
                        openGroupCreatedModalBottomSheet()
                    }
                    DeviceTransferRole.RECEIVE_BUT_DISCOVERING_PEER -> {
                        openPeersDiscoveryModalBottomSheet()
                    }
                }
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
                            // expand the bottom sheet to show receive has started
                            mainActivityViewModel.expandedConnectedToPeerReceiveOngoing()
                            receivedDataViewModel.onDataReceiveStarted(
                                ReceivedDataItem(
                                    dataDisplayName,
                                    dataSize,
                                    percentageOfDataRead,
                                    dataType,
                                    dataUri
                                )
                            )
                        }

                        DataToTransfer.TransferStatus.RECEIVE_ONGOING -> {
                            receivedDataViewModel.updateOngoingReceiveDataItemReceivePercent(
                                percentageOfDataRead
                            )
                        }

                        DataToTransfer.TransferStatus.RECEIVE_COMPLETE -> {
                            receivedDataViewModel.addDataToReceivedItems(
                                when (dataType) {
                                    MediaType.Image.value -> {
                                        DataToTransfer.DeviceImage(
                                            imageId = 0L,
                                            imageUri = dataUri!!,
                                            imageDateModified = "",
                                            imageMimeType = "image/*",
                                            imageSize = dataSize,
                                            imageBucketName = "ZipBolt Images",
                                            imageDisplayName = dataDisplayName
                                        )
                                    }
                                    MediaType.Video.value -> {
                                        DataToTransfer.DeviceVideo(
                                            videoId = 0L,
                                            videoUri = dataUri!!,
                                            videoDisplayName = dataDisplayName,
                                            videoDuration = dataUri.getVideoDuration(this@MainActivity),
                                            videoSize = dataSize
                                        )
                                    }
                                    MediaType.Audio.value -> {
                                        DataToTransfer.DeviceAudio(
                                            audioUri = dataUri!!,
                                            audioDisplayName = dataDisplayName,
                                            audioSize = dataSize,
                                            audioDuration = dataUri.getAudioDuration(this@MainActivity),
                                            audioArtPath = Uri.parse("")
                                        )
                                    }
                                    MediaType.App.value -> {
                                        DataToTransfer.DeviceApplication(
                                            applicationName = dataDisplayName,
                                            apkPath = dataUri!!.path ?: "",
                                            appSize = dataSize,
                                            applicationIcon = try {
                                                dataUri?.path!!.let { path ->
                                                    packageManager.getPackageArchiveInfo(path, 0)
                                                        .let { packageInfo ->
                                                            packageManager.getApplicationIcon(
                                                                packageInfo!!
                                                                    .applicationInfo.apply {
                                                                        sourceDir = path
                                                                        publicSourceDir = path
                                                                    })
                                                        }
                                                }
                                            } catch (nullPointerException: NullPointerException) {
                                                null
                                            }
                                        ).apply {
                                            this.dataType = dataType
                                        }
                                    }
                                    in MediaType.File.ImageFile.value
                                            ..MediaType.File.Document.DatDocument.value -> {
                                        DataToTransfer.DeviceFile(
                                            dataUri!!.toFile()
                                        ).apply {
                                            this.dataType = dataType
                                        }
                                    }

                                    else -> {
                                        DataToTransfer.DeviceImage(
                                            imageId = 0L,
                                            imageUri = dataUri!!,
                                            imageDateModified = "",
                                            imageMimeType = "image/*",
                                            imageSize = dataSize,
                                            imageBucketName = "ZipBolt Images",
                                            imageDisplayName = dataDisplayName
                                        )
                                    }
                                }.apply {
                                    transferStatus = DataToTransfer.TransferStatus.RECEIVE_COMPLETE
                                }
                            )
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
                        sentDataViewModel.changeCurrentDataToTransferDataItem(dataToTransfer)
                        sentDataViewModel.setCurrentDataToTransferPercentTransferred(
                            percentTransferred
                        )
                    }
                    DataToTransfer.TransferStatus.TRANSFER_COMPLETE -> {
                        sentDataViewModel.dataTransferCompleted(dataToTransfer)
                        sentDataViewModel.setCurrentDataToTransferPercentTransferred(
                            percentTransferred
                        )
                    }
                    DataToTransfer.TransferStatus.TRANSFER_ONGOING -> {
                        sentDataViewModel.setCurrentDataToTransferPercentTransferred(
                            percentTransferred
                        )
                    }
                    DataToTransfer.TransferStatus.TRANSFER_CANCELLED -> {
                        // from the cancelled media item from the queue of data in transfer
                        sentDataViewModel.cancelDataTransfer(dataToTransfer)
                    }
                    DataToTransfer.TransferStatus.NO_ACTION -> {

                    }
                    DataToTransfer.TransferStatus.TRANSFER_WAITING -> {

                    }
                }
            }
        }
    }

    // ui variables
    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var connectionOptionsBottomSheetDialog: BottomSheetDialog
    private var isSearchingForPeersBottomSheetLayoutConfigured: Boolean = false
    private var isConnectedToPeerNoActionBottomSheetLayoutConfigured: Boolean = false
    private var isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured: Boolean = false
    private var isWaitingForReceiverBottomSheetLayoutConfigured: Boolean = false
    private var shouldStopPeerDiscovery: Boolean = false
    private var startPeerDiscovery: Boolean = false

    private val discoveredPeersRecyclerViewAdapter: DiscoveredPeersRecyclerViewAdapter by lazy {
        DiscoveredPeersRecyclerViewAdapter(
            connectToDeviceClickListener = object :
                DiscoveredPeersRecyclerViewAdapter.ConnectToDeviceClickListener {
                override fun onConnectToDevice(wifiP2pDevice: WifiP2pDevice) {
                    // connectToADevice(wifiP2pDevice)
                    // startPeerDiscovery = false
                }
            }
        )
    }

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

    private val waitingForReceiverPersistentBottomSheetLayoutBinding:
            WaitingForReceiverPersistentBottomSheetLayoutBinding by lazy {
        MainActivityDataBindingUtils.getWaitingForReceiverPersistentBottomSheetBinding(this)
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

    private val waitingForReceiverBottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(
            waitingForReceiverPersistentBottomSheetLayoutBinding.root
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
            if (deviceTransferRole == DeviceTransferRole.SEND_BUT_DISCOVERING_PEER
                && groupCreatedFragment == null
            ) {
                openGroupCreatedModalBottomSheet()
            } else if ((deviceTransferRole == DeviceTransferRole.RECEIVE_BUT_DISCOVERING_PEER
                        || deviceTransferRole == DeviceTransferRole.SEND_AND_RECEIVE_BUT_DISCOVERING)
                && peersDiscoveryFragment == null
            ) {
                openPeersDiscoveryModalBottomSheet()
            }
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
            deviceTransferRole = when (deviceTransferRole) {
                DeviceTransferRole.SEND_BUT_DISCOVERING_PEER -> {
                    groupCreatedFragment?.dismiss()
                    groupCreatedFragment = null
                    DeviceTransferRole.SEND
                }
                DeviceTransferRole.RECEIVE_BUT_DISCOVERING_PEER -> {
                    peersDiscoveryFragment?.dismiss()
                    peersDiscoveryFragment = null
                    DeviceTransferRole.RECEIVE
                }
                DeviceTransferRole.NO_ROLE -> {
                    DeviceTransferRole.NO_ROLE
                }
                DeviceTransferRole.SEND_AND_RECEIVE_BUT_DISCOVERING -> {
                    peersDiscoveryFragment?.dismiss()
                    peersDiscoveryFragment = null
                    DeviceTransferRole.SEND_AND_RECEIVE
                }
                else -> {
                    deviceTransferRole
                }
            }

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
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    serviceIntent
                                )
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
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    serviceIntent
                                )
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
                    //beginPeerDiscovery()
                }
            }
        }

        override fun wifiP2pDiscoveryStarted() {
            displayToast("Discovery started")
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
        PermissionUtils.checkReadAndWriteExternalStoragePermission(this)
        registerReceiver(upgradedWifiDirectBroadcastReceiver, createSystemBroadcastIntentFilter())
        localBroadcastManager.registerReceiver(
            dataTransferServiceConnectionStateReceiver,
            IntentFilter().apply {
                addAction(DataTransferServiceConnectionStateReceiver.ACTION_DISCONNECTED_FROM_PEER)
                addAction(DataTransferServiceConnectionStateReceiver.ACTION_CANNOT_CONNECT_TO_PEER_ADDRESS)
            }
        )

        inflate(layoutInflater).apply {
            activityMainBinding = this
            connectToPeerButton.setOnClickListener {
                if (it.alpha > 0f) {
                    configureConnectionOptionsModalBottomSheetLayout()
                    connectionOptionsBottomSheetDialog.show()
                }
            }

            sendFileButton.setOnClickListener {
                if (it.alpha != 0f) {
                    // send broadcast event that send data button has been triggered
                    localBroadcastManager.sendBroadcast(sendDataClickedIntent)

                    mainActivityViewModel.expandedConnectedToPeerTransferOngoing()
                    // transfer data using the DataTransferService
                    dataTransferService?.transferData(
                        dataToTransferViewModel.collectionOfDataToTransfer
                    )
                    sentDataViewModel.addCollectionOfDataToTransferToSentDataItems(
                        dataToTransferViewModel.collectionOfDataToTransfer
                    )
                    // clear collection of data to transfer since transfer has been completed
                    dataToTransferViewModel.clearCollectionOfDataToTransfer()
                    dataToTransferViewModel.sentDataButtonClicked()
                }
            }

            with(mainActivityAllMediaOnDevice) {
                // change the tab mode based on the current screen density
                allMediaOnDeviceTabLayout.tabMode = if (resources.configuration.fontScale > 1.1) {
                    TabLayout.MODE_SCROLLABLE
                } else TabLayout.MODE_FIXED

                tabLayoutViewPagerConfiguration(
                    allMediaOnDeviceTabLayout,
                    allMediaOnDeviceViewPager,
                    AllMediaOnDeviceViewPagerAdapter(supportFragmentManager),
                    "Apps",
                    "Images",
                    "Videos",
                    "Music",
                    "Files"
                )
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

    private fun tabLayoutViewPagerConfiguration(
        tabLayout: TabLayout, viewPager: ViewPager,
        viewPagerAdapter: FragmentStatePagerAdapter,
        vararg tabNames: String
    ) {
        for (tabName in tabNames) {
            tabLayout.addTab(tabLayout.newTab().setText(tabName))
        }

        viewPager.adapter = viewPagerAdapter
        viewPager.addOnPageChangeListener(
            TabLayout.TabLayoutOnPageChangeListener(tabLayout)
        )

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    viewPager.currentItem = tab.position
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.let {
                    viewPager.currentItem = tab.position
                }
            }
        })
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
                        if (!isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured) {
                            configureConnectedToPeerTransferOngoingBottomSheetLayout()
                        }

                        with(connectedToPeerTransferOngoingBottomSheetBehavior) {
                            state =
                                BottomSheetBehavior.STATE_EXPANDED
                            peekHeight =
                                getBottomSheetPeekHeight()
                        }
                        // hide the connected to pair no action bottom sheet
                        if (isConnectedToPeerNoActionBottomSheetLayoutConfigured) {
                            with(connectedToPeerNoActionBottomSheetBehavior) {
                                isHideable = true
                                state = BottomSheetBehavior.STATE_HIDDEN
                            }
                        }

                        // if waiting for receive bottom sheet is configured hide it
                        if (isWaitingForReceiverBottomSheetLayoutConfigured) {
                            waitingForReceiverBottomSheetBehavior.run {
                                isHideable = true
                                state = BottomSheetBehavior.STATE_HIDDEN
                            }
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
                        if (isSearchingForPeersBottomSheetLayoutConfigured) {
                            searchingForPeersBottomSheetBehavior.run {
                                isHideable = true
                                state = BottomSheetBehavior.STATE_HIDDEN
                            }
                        } else if (isWaitingForReceiverBottomSheetLayoutConfigured) {
                            waitingForReceiverBottomSheetBehavior.run {
                                isHideable = true
                                state = BottomSheetBehavior.STATE_HIDDEN
                            }
                        }

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
                    PeerConnectionUIState.CollapsedWaitingForReceiver -> {
                        if (!isWaitingForReceiverBottomSheetLayoutConfigured) {
                            configureWaitingForReceiverBottomSheetLayout()
                        }
                        waitingForReceiverBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_COLLAPSED
                        waitingForReceiverBottomSheetBehavior.peekHeight =
                            getBottomSheetPeekHeight()
                    }
                    PeerConnectionUIState.ExpandedWaitingForReceiver -> {
                        if (!isWaitingForReceiverBottomSheetLayoutConfigured) {
                            configureWaitingForReceiverBottomSheetLayout()
                        }
                        waitingForReceiverBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_EXPANDED
                        waitingForReceiverBottomSheetBehavior.peekHeight =
                            getBottomSheetPeekHeight()
                    }
                }
            }
        }
    }

    private fun configureWaitingForReceiverBottomSheetLayout() {
        isWaitingForReceiverBottomSheetLayoutConfigured = true
        waitingForReceiverPersistentBottomSheetLayoutBinding.run {
            // collapsed layout
            waitingForReceiverPersistentBottomSheetLayoutCollapsedWaitingForReceiverLayout.run {
                root.animate().alpha(0f)
            }

            waitingForReceiverPersistentBottomSheetLayoutExpandedWaitingForReceiverLayout.run {

            }
        }

        waitingForReceiverBottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    mainActivityViewModel.expandedWaitingForReceiver()
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    mainActivityViewModel.collapsedWaitingForReceiver()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                waitingForReceiverPersistentBottomSheetLayoutBinding.run {
                    waitingForReceiverPersistentBottomSheetLayoutCollapsedWaitingForReceiverLayout.root.alpha =
                        1 - slideOffset * 3.5f
                    waitingForReceiverPersistentBottomSheetLayoutExpandedWaitingForReceiverLayout.root.alpha =
                        slideOffset
                }
            }
        })
    }

    private fun configureConnectedToPeerTransferOngoingBottomSheetLayout() {
        isConnectedToPeerTransferOngoingBottomSheetLayoutConfigured = true
        val sentAndReceivedDataItemsViewPagerAdapter = SentAndReceiveDataItemsViewPagerAdapter(
            supportFragmentManager, lifecycle, when (deviceTransferRole) {
                DeviceTransferRole.SEND -> true
                DeviceTransferRole.RECEIVE -> false
                DeviceTransferRole.SEND_AND_RECEIVE -> true
                else -> true
            }
        )
        with(connectedToPeerTransferOngoingBottomSheetLayoutBinding) {
            // configure collapsed connected to peer transfer ongoing layout
            with(collapsedConnectedToPeerOngoingDataTransferLayout) {
                root.animate().alpha(0f)
            }

            // configure expanded connected to peer transfer ongoing layout
            with(expandedConnectedToPeerTransferOngoingLayout) {
                with(expandedConnectedToPeerTransferOngoingToolbar) {
                    expandedBottomSheetLayoutToolbarTitleTextView.text =
                        getString(R.string.transfer_history)
                    expandedBottomSheetLayoutToolbarCancelButton.setOnClickListener {
                        // close the connection with the peer
                        dataTransferServiceIntent?.let {
                            unbindService(dataTransferServiceConnection)
                            stopService(dataTransferServiceIntent)
                        }
                    }
                    expandedBottomSheetLayoutToolbarCollapseBottomSheetButton.setOnClickListener {
                        // collapse the connected to peer transfer ongoing bottom sheet
                        connectedToPeerTransferOngoingBottomSheetBehavior.state =
                            BottomSheetBehavior.STATE_COLLAPSED
                        connectedToPeerTransferOngoingBottomSheetBehavior.peekHeight =
                            getBottomSheetPeekHeight()
                    }
                }
                expandedConnectedToPeerTransferOngoingViewPager2.adapter =
                    sentAndReceivedDataItemsViewPagerAdapter
                TabLayoutMediator(
                    expandedConnectedToPeerTransferOngoingTabLayout,
                    expandedConnectedToPeerTransferOngoingViewPager2
                ) { tab, position ->
                    tab.text = when (deviceTransferRole) {
                        DeviceTransferRole.SEND -> {
                            if (position == 0) {
                                "Sent"
                            } else {
                                "Received"
                            }
                        }
                        DeviceTransferRole.RECEIVE -> {
                            if (position == 0) {
                                "Received"
                            } else {
                                "Sent"
                            }
                        }
                        else -> {
                            if (position == 0) {
                                "Sent"
                            } else {
                                "Received"
                            }
                        }
                    }
                }.attach()
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

    private fun configureConnectedToPeerNoActionBottomSheetLayoutInfo(
        connectedDevice: WifiP2pDevice
    ) {
        isConnectedToPeerNoActionBottomSheetLayoutConfigured = true
        connectedToPeerNoActionBottomSheetLayoutBinding.run {
            collapsedConnectedToPeerNoActionLayout.run {
                deviceConnectedTo =
                    "Connected to ${connectedDevice.deviceName ?: "unknown device"}"
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
            expandedConnectedToPeerNoActionLayout.run {
                deviceAddress = connectedDevice.deviceAddress
                deviceName = connectedDevice.deviceName

                expandedConnectedToPeerNoActionTransferActionLabel.text =
                    when (deviceTransferRole) {
                        DeviceTransferRole.SEND -> {
                            getString(
                                R.string.you_can_transfer_files_now,
                                connectedDevice.deviceName
                            )
                        }
                        DeviceTransferRole.RECEIVE -> {
                            getString(
                                R.string.you_can_receive_files_now,
                                connectedDevice.deviceName
                            )
                        }
                        DeviceTransferRole.SEND_AND_RECEIVE -> {
                            getString(R.string.you_can_transfer_and_receive_files_now)
                        }
                        DeviceTransferRole.NO_ROLE -> {
                            getString(R.string.you_can_transfer_and_receive_files_now)
                        }
                        else -> ""
                    }

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
        connectionOptionsBottomSheetDialog = BottomSheetDialog(this)
        connectionOptionsBottomSheetDialog.setContentView(
            ZipBoltProConnectionOptionsBottomSheetLayoutBinding.inflate(layoutInflater).apply {
                zipBoltProConnectionOptionsBottomSheetLayoutSendCardView.setOnClickListener {
                    deviceTransferRole = DeviceTransferRole.SEND_BUT_DISCOVERING_PEER
                    // Turn on device wifi if it is off
                    if (!wifiManager.isWifiEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            turnOnWifiResultLauncher.launch(Intent(Settings.Panel.ACTION_WIFI))
                        } else {
                            if (wifiManager.setWifiEnabled(true)) {
                                /**Listen for wifi on via the broadcast receiver
                                 * and then call openGroupCreatedModalBottomSheet**/
                            } else {
                                displayToast("Turn off your hotspot")
                            }
                        }
                    } else {
                        if (isLocationPermissionGranted()) {
                            // Create wifi p2p group, if wifi is enabled
                            openGroupCreatedModalBottomSheet()
                        } else {
                            requestFineLocationPermission()
                        }
                    }
                    connectionOptionsBottomSheetDialog.dismiss()
                }
                zipBoltProConnectionOptionsBottomSheetLayoutReceiveCardView.setOnClickListener {
                    deviceTransferRole = DeviceTransferRole.RECEIVE_BUT_DISCOVERING_PEER
                    // Turn on device wifi
                    if (!wifiManager.isWifiEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            turnOnWifiResultLauncher.launch(Intent(Settings.Panel.ACTION_WIFI))
                        } else {
                            if (wifiManager.setWifiEnabled(true)) {
                                /**Listen for wifi on via the broadcast receiver
                                 * and then call openPeersDiscoveryModalBottomSheet**/

                            } else {
                                displayToast("Turn off your hotspot")
                            }
                        }
                    } else {
                        if (isLocationPermissionGranted()) {
                            // Create wifi p2p group, if wifi is enabled
                            openPeersDiscoveryModalBottomSheet()
                        } else {
                            requestFineLocationPermission()
                        }
                    }
                    connectionOptionsBottomSheetDialog.hide()
                }

                zipBoltProConnectionOptionsBottomSheetLayoutSendAndReceiveCardView.setOnClickListener {
                    deviceTransferRole = DeviceTransferRole.SEND_AND_RECEIVE_BUT_DISCOVERING
                }
            }.root
        )
    }

    private fun openGroupCreatedModalBottomSheet() {
        groupCreatedFragment = GroupCreatedFragment.newInstance()
        groupCreatedFragment?.isCancelable = false
        groupCreatedFragment?.show(
            supportFragmentManager,
            "GroupCreatedBottomSheetFragment"
        )
    }

    private fun openPeersDiscoveryModalBottomSheet() {
        peersDiscoveryFragment = PeersDiscoveryFragment.newInstance()
        peersDiscoveryFragment?.isCancelable = false
        peersDiscoveryFragment?.show(
            supportFragmentManager,
            "PeersDiscoveryBottomSheetFragment"
        )
    }

    fun connectedToDeviceSuccessfully() {
        startPeerDiscovery = false
        peersDiscoveryFragment?.dismiss()
    }

    fun deviceConnectionFailed() {

    }

    fun cancelOngoingDataTransfer() {
        dataTransferService?.cancelActiveTransfer()
    }

    fun cancelOngoingDataReceive() {
        dataTransferService?.cancelActiveReceive()
    }


    fun addToDataToTransferList(dataToTransfer: DataToTransfer) {
        dataToTransferViewModel.addDataToTransfer(dataToTransfer)
    }

    fun removeFromDataToTransferList(dataToTransfer: DataToTransfer) {
        dataToTransferViewModel.removeDataFromDataToTransfer(dataToTransfer)
    }


    private fun getBottomSheetPeekHeight(): Int {
        return (60 * resources.displayMetrics.density).roundToInt()
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
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
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

        upgradedWifiDirectBroadcastReceiver = UpgradedWifiDirectBroadcastReceiver(
            wifiDirectBroadcastReceiverCallback = wifiDirectBroadcastReceiverCallback,
            connectivityManager = connectivityManager,
            wifiP2pManager = wifiP2pManager,
            wifiP2pChannel = wifiP2pChannel
        )
        wifiP2pManager.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {

            }

            override fun onFailure(reason: Int) {

            }

        })
    }


    // check if SpeedForce has access to device fine location
    private fun requestFineLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission.ACCESS_FINE_LOCATION),
            FINE_LOCATION_REQUEST_CODE
        )
    }


    private fun isLocationPermissionGranted() = ActivityCompat.checkSelfPermission(
        this,
        permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun displayToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == FINE_LOCATION_REQUEST_CODE && permissions.contains(permission.ACCESS_FINE_LOCATION)) {
            if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when (deviceTransferRole) {
                        DeviceTransferRole.SEND_AND_RECEIVE_BUT_DISCOVERING -> {
                            openPeersDiscoveryModalBottomSheet()
                        }
                        DeviceTransferRole.SEND_BUT_DISCOVERING_PEER -> {
                            openGroupCreatedModalBottomSheet()
                        }
                        DeviceTransferRole.RECEIVE_BUT_DISCOVERING_PEER -> {
                            openPeersDiscoveryModalBottomSheet()
                        }
                        else -> {
                        }
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        unbindService(dataTransferServiceConnection)
        // unregister the broadcast receiver
        unregisterReceiver(upgradedWifiDirectBroadcastReceiver)
        localBroadcastManager.unregisterReceiver(dataTransferServiceConnectionStateReceiver)

    }

    override fun onBackPressed() {
        if (FilesFragment.backStackCount > 0) {
            if (popBackStackListener?.popStack() == true) {

            } else {
                super.onBackPressed()
            }
        } else super.onBackPressed()
    }
}