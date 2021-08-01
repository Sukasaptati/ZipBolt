package com.salesground.zipbolt.communication.implementation

import com.salesground.zipbolt.communication.MediaTransferProtocol
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.model.DocumentType
import com.salesground.zipbolt.repository.SavedFilesRepository
import com.salesground.zipbolt.service.DataTransferService
import java.io.*
import java.util.*
import kotlin.math.min

class DirectoryMediaTransferProtocol(
    private val savedFilesRepository: SavedFilesRepository
) : MediaTransferProtocol {
    private val transferBuffer = ByteArray(DataTransferService.BUFFER_SIZE)
    private val receiveBuffer = ByteArray(DataTransferService.BUFFER_SIZE)
    private var dataSizeTransferredFromDirectory: Long = 0L
    private var dataSizeReadFromSocket: Long = 0L
    private val zipBoltBaseFolderDirectory: File by lazy {
        savedFilesRepository.getZipBoltMediaCategoryBaseDirectory(SavedFilesRepository.ZipBoltMediaCategory.FOLDERS_BASE_DIRECTORY)
    }

    override fun cancelCurrentTransfer(transferMetaData: MediaTransferProtocol.MediaTransferProtocolMetaData) {

    }

    override suspend fun transferMedia(
        dataToTransfer: DataToTransfer,
        dataOutputStream: DataOutputStream,
        dataTransferListener: MediaTransferProtocol.DataTransferListener
    ) {
        dataToTransfer as DataToTransfer.DeviceFile
        dataToTransfer.dataSize = dataToTransfer.file.getDirectorySize()

        // send directory name and directory siz e
        dataOutputStream.writeUTF(dataToTransfer.file.name)
        dataOutputStream.writeLong(dataToTransfer.dataSize)

        val directoryChildren = dataToTransfer.file.listFiles()
        directoryChildren?.let {
            dataOutputStream.writeInt(directoryChildren.size)
            it.forEach { directoryChild ->
                if (directoryChild.isDirectory) {
                    dataOutputStream.writeInt(DocumentType.Directory.value)
                    transferDirectory(
                        dataOutputStream,
                        dataToTransfer,
                        directoryChild,
                        dataTransferListener
                    )
                } else {
                    dataOutputStream.writeInt(DataToTransfer.MediaType.FILE.value)
                    dataOutputStream.writeUTF(directoryChild.name)
                    var directoryChildFileLength = directoryChild.length()
                    dataOutputStream.writeLong(directoryChildFileLength)
                    val fileDataInputStream = DataInputStream(
                        BufferedInputStream(FileInputStream(directoryChild))
                    )

                    dataTransferListener.onTransfer(
                        dataToTransfer,
                        0f,
                        DataToTransfer.TransferStatus.TRANSFER_STARTED
                    )
                    var lengthRead: Int = 0
                    while (directoryChildFileLength > 0) {
                        fileDataInputStream.readFully(
                            transferBuffer, 0, min(
                                directoryChildFileLength,
                                transferBuffer.size.toLong()
                            ).toInt()
                        )
                        lengthRead =
                            min(directoryChildFileLength, transferBuffer.size.toLong()).toInt()
                        directoryChildFileLength -= lengthRead
                        dataSizeTransferredFromDirectory += lengthRead
                        dataOutputStream.write(
                            transferBuffer,
                            0, lengthRead
                        )
                        directoryChildFileLength += lengthRead

                        dataTransferListener.onTransfer(
                            dataToTransfer,
                            ((dataToTransfer.dataSize - dataSizeTransferredFromDirectory) / dataToTransfer.dataSize.toFloat()),
                            DataToTransfer.TransferStatus.TRANSFER_ONGOING
                        )
                    }
                }
            }
        }
    }

    private fun transferDirectory(
        dataOutputStream: DataOutputStream,
        originalDataToTransfer: DataToTransfer,
        directory: File,
        dataTransferListener: MediaTransferProtocol.DataTransferListener,
    ) {
        dataOutputStream.writeUTF(directory.name)
        val directoryChildren = directory.listFiles()
        directoryChildren?.let {
            dataOutputStream.writeInt(directoryChildren.size)
            it.forEach { directoryChild ->
                if (directoryChild.isDirectory) {
                    dataOutputStream.writeInt(DocumentType.Directory.value)
                    transferDirectory(
                        dataOutputStream,
                        originalDataToTransfer,
                        directoryChild,
                        dataTransferListener
                    )
                } else {
                    dataOutputStream.writeInt(DataToTransfer.MediaType.FILE.value)
                    dataOutputStream.writeUTF(directoryChild.name)
                    var directoryChildFileLength = directoryChild.length()
                    dataOutputStream.writeLong(directoryChildFileLength)
                    val fileDataInputStream = DataInputStream(
                        BufferedInputStream(FileInputStream(directoryChild))
                    )

                    dataTransferListener.onTransfer(
                        originalDataToTransfer,
                        0f,
                        DataToTransfer.TransferStatus.TRANSFER_STARTED
                    )
                    var lengthRead: Int = 0
                    while (directoryChildFileLength > 0) {
                        fileDataInputStream.readFully(
                            transferBuffer, 0, min(
                                directoryChildFileLength,
                                transferBuffer.size.toLong()
                            ).toInt()
                        )
                        lengthRead =
                            min(directoryChildFileLength, transferBuffer.size.toLong()).toInt()
                        directoryChildFileLength -= lengthRead
                        dataSizeTransferredFromDirectory += lengthRead
                        dataOutputStream.write(
                            transferBuffer,
                            0, lengthRead
                        )
                        directoryChildFileLength += lengthRead

                        dataTransferListener.onTransfer(
                            originalDataToTransfer,
                            ((originalDataToTransfer.dataSize - dataSizeTransferredFromDirectory) / originalDataToTransfer.dataSize.toFloat()),
                            DataToTransfer.TransferStatus.TRANSFER_ONGOING
                        )
                    }
                }
            }
        }
    }

    override suspend fun receiveMedia(
        dataInputStream: DataInputStream,
        dataReceiveListener: MediaTransferProtocol.DataReceiveListener
    ) {
        // read the directory name and size
        val initialDirectoryName = dataInputStream.readUTF()
        val initialDirectorySize = dataInputStream.readLong()
        // create directory file in base folders directory
        val directoryFile = File(zipBoltBaseFolderDirectory, initialDirectoryName)
        val directoryChildrenCount = dataInputStream.readInt()

        for (i in 0 until directoryChildrenCount) {
            // read child type
            val childType = dataInputStream.readInt()
            if (childType == DocumentType.Directory.value) {

            } else {
                // read file name
                val fileName = dataInputStream.readUTF()
                // read file size
                var fileSize = dataInputStream.readLong()
                val directoryChild = File(directoryFile, fileName)
                val directoryChildBufferedOS = BufferedOutputStream(
                    FileOutputStream(
                        directoryChild
                    )
                )
                while (fileSize > 0) {
                    dataInputStream.readFully(
                        receiveBuffer, 0,
                        min(receiveBuffer.size.toLong(), fileSize).toInt()
                    )
                    directoryChildBufferedOS.write(
                        receiveBuffer, 0,
                        min(receiveBuffer.size.toLong(), fileSize).toInt()
                    )
                    dataSizeReadFromSocket += min(
                        receiveBuffer.size.toLong(),
                        fileSize
                    )
                    fileSize -= min(
                        receiveBuffer.size.toLong(),
                        fileSize
                    )
                    dataReceiveListener.onReceive(
                        initialDirectoryName,
                        initialDirectorySize,
                        (initialDirectorySize - dataSizeReadFromSocket) / initialDirectorySize.toFloat(),
                        DocumentType.Directory.value,
                        null,
                        DataToTransfer.TransferStatus.TRANSFER_ONGOING
                    )
                }
            }
        }
    }

    private suspend fun receiveDirectory(
        dataInputStream: DataInputStream,
        dataReceiveListener: MediaTransferProtocol.DataReceiveListener,
        parentDirectory: File,
        initialDirectoryName: String,
        initialDirectorySize: Long
    ) {
        // read the directory name and child count
        val directoryName = dataInputStream.readUTF()
        val directoryChildCount = dataInputStream.readInt()

        for (i in 0 until directoryChildCount) {
            // read child type
            val directoryChildType = dataInputStream.readInt()
            if (directoryChildType == DocumentType.Directory.value) {

            } else {
                // read file name  and length
                val directoryChildFileName = dataInputStream.readUTF()
                var directoryChildFileSize = dataInputStream.readLong()

                val directoryChildFile = File(
                    parentDirectory,
                    directoryChildFileName
                )
                val directoryChildFileBOS =
                    BufferedOutputStream(FileOutputStream(directoryChildFile))
                var readSize: Int = 0
                while (directoryChildFileSize > 0) {
                    readSize = min(
                        receiveBuffer.size.toLong(),
                        directoryChildFileSize
                    ).toInt()
                    dataInputStream.readFully(
                        receiveBuffer,
                        0, readSize
                    )
                    directoryChildFileBOS.write(
                        receiveBuffer,
                        0, readSize
                    )

                    directoryChildFileSize -= readSize
                    dataSizeReadFromSocket += readSize
                    dataReceiveListener.onReceive(
                        initialDirectoryName,
                        initialDirectorySize,
                        ((initialDirectorySize - dataSizeReadFromSocket) / initialDirectorySize.toFloat()) * 100f,
                        DocumentType.Directory.value,
                        null,
                        DataToTransfer.TransferStatus.RECEIVE_ONGOING
                    )
                }
            }
        }
    }

    private fun File.getDirectorySize(): Long {
        var directorySize = 0L
        val directoryStack: Deque<File> = ArrayDeque()
        directoryStack.push(this)
        var directory: File

        while (directoryStack.isNotEmpty()) {
            directory = directoryStack.pop()
            directory.listFiles()?.forEach {
                if (it.isDirectory) {
                    directoryStack.add(it)
                } else {
                    directorySize += it.length()
                }
            }
        }
        return directorySize
    }
}