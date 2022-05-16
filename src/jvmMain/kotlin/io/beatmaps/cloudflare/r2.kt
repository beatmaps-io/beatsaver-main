package io.beatmaps.cloudflare

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import java.io.File

interface IR2Bucket {
    fun uploadFile(file: File)
    fun deleteFile(key: String)
}

class R2(accountId: String, accessKey: String, secretKey: String) {
    private val r2Client: AmazonS3

    init {
        r2Client = initAmazonS3Client("https://$accountId.r2.cloudflarestorage.com", accessKey, secretKey)
    }

    private class R2Bucket(val r2: R2, val bucketName: String): IR2Bucket {
        override fun uploadFile(file: File) {
            r2.uploadFile(bucketName, file)
        }

        override fun deleteFile(key: String) {
            r2.deleteFile(bucketName, key)
        }
    }

    private fun initAmazonS3Client(
        endpoint: String,
        accessKey: String,
        secretKey: String
    ): AmazonS3 =
        AmazonS3ClientBuilder.standard()
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(accessKey, secretKey)
                )
            )
            .withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(endpoint, "")
            )
            .withPathStyleAccessEnabled(true)
            .build()

    fun getBucket(bucketName: String): IR2Bucket = R2Bucket(this, bucketName)

    private fun uploadFile(bucketName: String, file: File) {
        r2Client.putObject(bucketName, file.name, file)
    }

    private fun deleteFile(bucketName: String, key: String) {
        r2Client.deleteObject(bucketName, key)
    }
}