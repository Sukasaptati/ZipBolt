package com.salesground.zipbolt.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.*
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build

class UpgradedWifiDirectBroadcastReceiver(
    private val wifiDirectBroadcastReceiverCallback: WifiDirectBroadcastReceiver.WifiDirectBroadcastReceiverCallback,
    private val connectivityManager: ConnectivityManager,
    private val wifiP2pManager: WifiP2pManager,
    private val wifiP2pChannel: WifiP2pManager.Channel
) : BroadcastReceiver() {

    interface WifiDirectBroadcastReceiverCallback {
        fun wifiOn()
        fun wifiOff()
        fun peersListAvailable(peersList: MutableList<WifiP2pDevice>)
        fun connectedToPeer(
            wifiP2pInfo: WifiP2pInfo,
            peeredDevice: WifiP2pDevice
        )

        fun wifiP2pDiscoveryStopped()
        fun wifiP2pDiscoveryStarted()
        fun disconnectedFromPeer()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (isConnectedToPeerNetwork(intent)) {
                        wifiP2pManager.requestConnectionInfo(wifiP2pChannel) { wifiP2pInfo ->
                            if (wifiP2pInfo.groupFormed) {
                                intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                                    ?.let { wifiP2pGroup ->
                                        val connectedDevice: WifiP2pDevice =
                                            if (wifiP2pInfo.isGroupOwner) {
                                                if (wifiP2pGroup.clientList.isEmpty()) return@let
                                                wifiP2pGroup.clientList.first()
                                            } else {
                                                wifiP2pGroup.owner
                                            }

                                        wifiDirectBroadcastReceiverCallback.connectedToPeer(
                                            wifiP2pInfo,
                                            connectedDevice
                                        )
                                    }
                            }
                        }
                    }
                }
                else -> {

                }
            }
        }
    }

    private fun isConnectedToPeerNetwork(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.let { networkCapabilities: NetworkCapabilities ->
                    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P)
                }

        } else {
            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                ?.let { networkInfo: NetworkInfo ->
                    return networkInfo.isConnected
                }
        }
        return false
    }
}