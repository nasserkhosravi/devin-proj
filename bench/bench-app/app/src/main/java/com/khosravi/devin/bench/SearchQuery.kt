package com.khosravi.devin.bench

import java.net.URLEncoder

object SearchQuery {

    fun path(text: String, sort: Sort): String {
        val q = URLEncoder.encode(text, "UTF-8")
        return "/search?q=$q&sort=${sort.apiValue}"
    }
}
