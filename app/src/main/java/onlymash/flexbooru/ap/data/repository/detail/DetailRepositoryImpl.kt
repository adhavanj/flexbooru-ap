package onlymash.flexbooru.ap.data.repository.detail

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onlymash.flexbooru.ap.data.api.Api
import onlymash.flexbooru.ap.data.db.dao.DetailDao
import onlymash.flexbooru.ap.data.model.Detail
import onlymash.flexbooru.ap.data.model.VoteResponse
import onlymash.flexbooru.ap.extension.NetResult
import onlymash.flexbooru.ap.extension.getPostDetailUrl
import onlymash.flexbooru.ap.extension.getVoteUrl
import retrofit2.HttpException
import kotlin.Exception

class DetailRepositoryImpl(private val api: Api,
                           private val detailDao: DetailDao) : DetailRepository {

    override suspend fun getDetail(
        scheme: String,
        host: String,
        postId: Int,
        token: String): NetResult<Detail> {
        return withContext(Dispatchers.IO) {
            var detail: Detail? = detailDao.getDetailById(postId)
            if (detail != null) {
                NetResult.Success(detail)
            } else {
                try {
                    val response = api.getDetail(
                        getPostDetailUrl(
                            scheme = scheme,
                            host = host,
                            postId = postId,
                            token = token
                        )
                    ).execute()
                    if (response.isSuccessful) {
                        detail = response.body()
                        if (detail == null) {
                            NetResult.Error("Empty")
                        } else {
                            detailDao.insert(detail)
                            NetResult.Success(detail)
                        }
                    } else {
                        NetResult.Error("code: ${response.code()}")
                    }
                } catch (e: Exception) {
                    if (e is HttpException) {
                        NetResult.Error("code: ${e.code()}")
                    } else {
                        NetResult.Error(e.message.toString())
                    }
                }
            }
        }
    }

    override suspend fun getLocalDetails(): LiveData<List<Detail>> {
        return withContext(Dispatchers.IO) {
            detailDao.getAllDetailsLivaData()
        }
    }

    override suspend fun votePost(
        scheme: String,
        host: String,
        vote: Int,
        token: String,
        detail: Detail
    ): NetResult<VoteResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.vote(
                    url = getVoteUrl(scheme, host),
                    postId = detail.id,
                    vote = vote,
                    token = token
                ).execute()
                val data = response.body()
                when {
                    response.isSuccessful && data != null-> {
                        detail.scoreNumber = data.scoreN
                        detail.starIt = vote > 0
                        detailDao.update(detail)
                        NetResult.Success(data)
                    }
                    response.code() == 400 -> NetResult.Error("token error")
                    else -> NetResult.Error("code: ${response.code()}")
                }
            } catch (e: Exception) {
                NetResult.Error(e.message.toString())
            }
        }
    }
}