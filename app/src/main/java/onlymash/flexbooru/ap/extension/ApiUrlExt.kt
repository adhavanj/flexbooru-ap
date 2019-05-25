package onlymash.flexbooru.ap.extension

import okhttp3.HttpUrl
import onlymash.flexbooru.ap.data.Search
import onlymash.flexbooru.ap.data.SearchType

fun Search.getPostsUrl(page: Int): HttpUrl {
    val builder = HttpUrl.Builder()
        .scheme(scheme)
        .host(host)
        .addPathSegments("pictures/view_posts/$page")
        .addQueryParameter("lang", "en")
        .addQueryParameter("type", "json")
        .addQueryParameter("posts_per_page", limit.toString())
        .addQueryParameter("order_by", "date")
        .addQueryParameter("ldate", "0")
        .addQueryParameter("token", token)
    if (query.isNotEmpty() && type == SearchType.NORMAL) {
        builder.addQueryParameter("search_tag", query)
    }
    if (type == SearchType.FAVORITE) {
        builder.addQueryParameter("stars_by", userId.toString())
    }
    return builder.build()
}

fun getPostDetailUrl(
    scheme: String,
    host: String,
    postId: Int,
    token: String): HttpUrl {

    return HttpUrl.Builder()
        .scheme(scheme)
        .host(host)
        .addPathSegments("pictures/view_post/$postId")
        .addQueryParameter("lang", "en")
        .addQueryParameter("type", "json")
        .addQueryParameter("token", token)
        .build()
}

fun getLoginUrl(scheme: String, host: String): HttpUrl {
    return HttpUrl.Builder()
        .scheme(scheme)
        .host(host)
        .addPathSegments("login/submit")
        .build()
}

fun getVoteUrl(scheme: String, host: String): HttpUrl {
    return HttpUrl.Builder()
        .scheme(scheme)
        .host(host)
        .addPathSegments("pictures/vote")
        .build()
}