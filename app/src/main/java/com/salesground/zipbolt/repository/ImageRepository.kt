package com.salesground.zipbolt.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.salesground.zipbolt.model.MediaCategory
import com.salesground.zipbolt.model.MediaModel
import com.salesground.zipbolt.repository.repositoryinterface.ImageRepositoryInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.intellij.lang.annotations.Language
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject
import kotlin.math.min


class ImageRepository @Inject constructor
    (@ApplicationContext private val applicationContext: Context) : ImageRepositoryInterface {


    fun fetchAllImagesOnDeviceOnce(): MutableList<MediaModel> {
        val allImagesOnDevice: MutableList<MediaModel> = mutableListOf()

        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATA
            )
        }

        val selection = null
        val selectionArgs = null
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} ASC"

        applicationContext.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.apply {
            val imageIdColumnIndex = getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val imageDateAddedColumnIndex =
                getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val imageDisplayNameColumnIndex =
                getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val imageSizeColumnIndex = getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val imageMimeTypeColumnIndex =
                getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)



            while (moveToNext()) {
                val imageId = getLong(imageIdColumnIndex)
                val imageDateAdded = getLong(imageDateAddedColumnIndex)
                val imageDisplayName = getString(imageDisplayNameColumnIndex)
                val imageSize = getLong(imageSizeColumnIndex)
                val imageMimeType = getString(imageMimeTypeColumnIndex)

                val imageUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageId
                )
                val imageParentFolderName =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val imageBucketNameColumnIndex =
                            getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        getString(imageBucketNameColumnIndex)
                    } else {
                        val imageDataColumnIndex = getColumnIndex(MediaStore.Images.Media.DATA)
                        File(getString(imageDataColumnIndex)).parentFile!!.name
                    }



                allImagesOnDevice.add(
                    MediaModel(
                        mediaUri = imageUri,
                        mediaDateAdded = imageDateAdded,
                        mediaDisplayName = imageDisplayName,
                        mediaSize = imageSize,
                        mediaCategory = MediaCategory.IMAGE,
                        mimeType = imageMimeType,
                        mediaBucketName = imageParentFolderName,

                        )
                )
            }
        }
        return allImagesOnDevice
    }

    override fun fetchAllImagesOnDevice() = flow {
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATA
            )
        }

        val selection = null
        val selectionArgs = null
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} ASC"

        applicationContext.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.apply {
            val imageIdColumnIndex = getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val imageDateAddedColumnIndex =
                getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val imageDisplayNameColumnIndex =
                getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val imageSizeColumnIndex = getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val imageMimeTypeColumnIndex =
                getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)



            while (moveToNext()) {
                val imageId = getLong(imageIdColumnIndex)
                val imageDateAdded = getLong(imageDateAddedColumnIndex)
                val imageDisplayName = getString(imageDisplayNameColumnIndex)
                val imageSize = getLong(imageSizeColumnIndex)
                val imageMimeType = getString(imageMimeTypeColumnIndex)

                val imageUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageId
                )
                val imageParentFolderName =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val imageBucketNameColumnIndex =
                            getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        getString(imageBucketNameColumnIndex)
                    } else {
                        val imageDataColumnIndex = getColumnIndex(MediaStore.Images.Media.DATA)
                        File(getString(imageDataColumnIndex)).parentFile!!.name
                    }


                emit(
                    MediaModel(
                        mediaUri = imageUri,
                        mediaDateAdded = imageDateAdded,
                        mediaDisplayName = imageDisplayName,
                        mediaSize = imageSize,
                        mediaCategory = MediaCategory.IMAGE,
                        mimeType = imageMimeType,
                        mediaBucketName = imageParentFolderName
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun convertImageModelToFile(imagesToConvert: MutableList<MediaModel>): MutableList<File> {
        val imageFiles: MutableList<File> = mutableListOf()
        val imageFolder = File(applicationContext.getExternalFilesDir(null), "SpeedForce")
        if (!imageFolder.exists()) imageFolder.mkdirs()

        imagesToConvert.forEach {
            val imageFile = File(imageFolder, it.mediaDisplayName)
            val imageFileOutputStream = FileOutputStream(imageFile)

            applicationContext.contentResolver.openFileDescriptor(it.mediaUri, "r")?.apply {
                val fileInputStream = FileInputStream(this.fileDescriptor)
                val buffer = ByteArray(1_000_000)
                var length: Int
                while (fileInputStream.read(buffer).also { length = it } != -1) {
                    imageFileOutputStream.write(buffer, 0, length)
                }
                fileInputStream.close()
            }
            imageFileOutputStream.flush()
            imageFileOutputStream.close()
            imageFiles.add(imageFile)
        }
        return imageFiles
    }

    fun searchForImageByNameInMediaStore(imageName: String) : Boolean {
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} == ?"
        val selectionArgs = arrayOf(imageName)
        val selectionOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC LIMIT 1"
        applicationContext.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            selectionOrder
        )?.let { cursor ->
            val imageDisplayNameColumnIndex =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                val retrievedImageDisplayName = cursor.getString(imageDisplayNameColumnIndex)
                if(retrievedImageDisplayName == imageName) return true
            }
        }
        return false
    }

    fun insertImageIntoMediaStore(
        mediaName: String?,
        mediaSize: Long,
        mediaMimeType: String,
        DIS: DataInputStream
    ) {
        var mediaSize1 = mediaSize

        val mainDirectory = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            File(Environment.DIRECTORY_PICTURES, "ZipBoltImages")
        else File(Environment.getExternalStorageDirectory(), "ZipBoltImages")
        if (!mainDirectory.exists()) {
            mainDirectory.mkdirs()
        }

        /* TODO Create a function that searches the mediaStore for an image with the exact same name
        as mediaName before trying to create a file for the image

        applicationContext.contentResolver.query(
             MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
             null,
             "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
             arrayOf(mediaName),
             "${MediaStore.Images.Media.DISPLAY_NAME} LIMIT 1"
         )*/

        val imageFile = File(mainDirectory, "Image" + System.currentTimeMillis() + ".jpg")
        // Log.i("NewTransfer", "right here in media store filename = ${imageFile.name}")

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
            put(MediaStore.Images.Media.TITLE, imageFile.name)
            put(MediaStore.Images.Media.SIZE, mediaSize1)
            put(MediaStore.Images.Media.MIME_TYPE, mediaMimeType)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.OWNER_PACKAGE_NAME, applicationContext.packageName)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/ZipBoltImages"
                )
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    mainDirectory.absolutePath
                )
            } else {
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
            }

        }

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val imageUri = applicationContext.contentResolver.insert(
            collection,
            contentValues
        )

        imageUri?.let {
            applicationContext.contentResolver.openFileDescriptor(imageUri, "w")?.let {
                val imageFileDataOutputStream = FileOutputStream(it.fileDescriptor)
                val bufferArray = ByteArray(10_000_000)

                while (mediaSize1 > 0) {
                    val bytesRead = DIS.read(
                        bufferArray,
                        0,
                        min(mediaSize1.toInt(), bufferArray.size)
                    )
                    if (bytesRead == -1) break
                    imageFileDataOutputStream.write(bufferArray, 0, bytesRead)
                    mediaSize1 -= bytesRead
                }
                imageFileDataOutputStream.flush()
                imageFileDataOutputStream.close()
                it.close()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                applicationContext.contentResolver.update(imageUri, contentValues, null, null)
            }
        }
    }
}