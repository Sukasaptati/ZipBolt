package com.salesground.zipbolt.di

import com.salesground.zipbolt.communicationprotocol.MediaTransferProtocol
import com.salesground.zipbolt.communicationprotocol.ZipBoltMediaTransferProtocol
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import javax.inject.Singleton


@Module
@InstallIn(ServiceComponent::class)
abstract class CommunicationDIModule {

    @Singleton
    @Binds
    abstract fun getMediaTransferProtocol(
        zipBoltMediaTransferProtocol:
        ZipBoltMediaTransferProtocol
    ): MediaTransferProtocol
}