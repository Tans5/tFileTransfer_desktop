package com.tans.tfiletranserdesktop.net.model

import com.squareup.moshi.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


@JsonClass(generateAdapter = false)
data class ResponseFolderModel(
    val path: String,
    @Json(name = "children_folders") val childrenFolders: List<Folder>,
    @Json(name = "children_files") val childrenFiles: List<File>
)


@JsonClass(generateAdapter = false)
data class Folder(
    val name: String,
    val path: String,
    @Json(name = "child_count") val childCount: Long,
    @Json(name = "last_modify") val lastModify: OffsetDateTime
)


@JsonClass(generateAdapter = false)
data class File(
    val name: String,
    val path: String,
    val size: Long,
    @Json(name = "last_modify") val lastModify: OffsetDateTime
)


@JsonClass(generateAdapter = false)
data class FileMd5(
        /**
         * This is not file's md5, and use path of file calculate md5, because calculate big files' md5 too slow.
         */
        val md5: ByteArray,
        val file: File
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileMd5

        if (!md5.contentEquals(other.md5)) return false
        if (file != other.file) return false

        return true
    }

    override fun hashCode(): Int {
        var result = md5.contentHashCode()
        result = 31 * result + file.hashCode()
        return result
    }
}


class OffsetDataTimeJsonAdapter : JsonAdapter<OffsetDateTime>() {

    override fun fromJson(reader: JsonReader): OffsetDateTime? {
        val dateString = reader.nextString()
        return if (dateString != null) {
            OffsetDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
        } else {
            null
        }
    }

    override fun toJson(writer: JsonWriter, value: OffsetDateTime?) {
        writer.value(if (value != null) DateTimeFormatter.ISO_DATE_TIME.format(value) else null)
    }

}

val moshi = Moshi.Builder()
    .add(OffsetDateTime::class.java, OffsetDataTimeJsonAdapter())
    .build()