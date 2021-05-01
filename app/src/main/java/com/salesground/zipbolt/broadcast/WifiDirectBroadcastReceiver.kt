package com.salesground.zipbolt.broadcast

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import com.salesground.zipbolt.MainActivity

class WifiDirectBroadcastReceiver(
    private val wifiDirectBroadcastReceiverCallback: WifiDirectBroadcastReceiverCallback,
    private val wifiP2pManager: WifiP2pManager,
    private val wifiP2pChannel: WifiP2pManager.Channel
) : BroadcastReceiver() {

    interface WifiDirectBroadcastReceiverCallback {
        fun wifiOn()
        fun wifiOff()
        fun peersListAvailable(peersList: MutableList<WifiP2pDevice>)
        fun peeredDeviceConnectionInfoReady(wifiP2pInfo: WifiP2pInfo)
        fun wifiP2pDiscoveryStopped()
        fun wifiP2pDiscoveryStarted()
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(p0: Context?, p1: Intent?) {
        p1?.let { intent: Intent ->
            val action: String? = intent.action
            action.let {
                when (it) {
                    // Broadcast when Wi-Fi P2P is enabled or disabled on the device.
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val isWifiOn = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (isWifiOn == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            // wifiP2p ie enabled
                            wifiDirectBroadcastReceiverCallback.wifiOn()
                        } else {
                            // wifiP2p is not enabled
                            wifiDirectBroadcastReceiverCallback.wifiOff()
                        }

                    }

                    /* Broadcast when you call discoverPeers().
                        You will usually call requestPeers() to get an updated list of peers if you handle this intent in your application.
                        */
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        wifiP2pManager.requestPeers(wifiP2pChannel) { p0 ->
                            // potential peers are available
                            // Store this list in a viewModel to display it on the UI
                            p0?.deviceList?.let { collectionOfWifiP2pDevice ->
                                wifiDirectBroadcastReceiverCallback.peersListAvailable(
                                    collectionOfWifiP2pDevice.toMutableList()
                                )
                            }
                        }
                    }

                    //Broadcast when the state of the device's Wi-Fi connection changes.
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                        /* val wifiP2pInfo = intent.getParcelableExtra<WifiP2pInfo>(
                             WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                         wifiP2pInfo?.let {
                             if (wifiP2pInfo.groupFormed) {
                                 wifiP2pManager.requestConnectionInfo(wifiP2pChannel) { wifiP2pInfo ->
                                     mainActivity.peeredDeviceConnectionInfoReady(
                                         deviceConnectionInfo = wifiP2pInfo
                                     )
                                 }
                             }
                         }*/
                        /* val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?

                         TODO NetworkInfo deprecated, go search for another alternative, until then request connection info directly
                         if(networkInfo?.isConnected == true){ }*/
                        // We are connected with the other device, request connection
                        // info to find group owner IP
                        wifiP2pManager.requestConnectionInfo(
                            wifiP2pChannel
                        ) { p0 ->
                            p0?.let { wifiP2pInfo ->
                                // send connected device the connection info to the mainActivityViewModel
                                // so we can create a socket connection and begin data transfer
                                wifiDirectBroadcastReceiverCallback.peeredDeviceConnectionInfoReady(
                                    wifiP2pInfo
                                )
                            }
                        }

                    }

                    // Broadcast when a device's details have changed, such as the device's name.
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {

                    }

                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                        val discoveryState = intent.getIntExtra(
                            WifiP2pManager.EXTRA_DISCOVERY_STATE,
                            -1
                        )
                        when (discoveryState) {
                            WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED -> {
                                wifiDirectBroadcastReceiverCallback.wifiP2pDiscoveryStarted()
                            }
                            WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED -> {
                                wifiDirectBroadcastReceiverCallback.wifiP2pDiscoveryStopped()
                            }
                        }
                    }
                }
            }
        }
    }
}