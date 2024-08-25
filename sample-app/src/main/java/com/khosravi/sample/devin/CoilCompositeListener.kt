package com.khosravi.sample.devin

import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult

class CoilCompositeListener : ImageRequest.Listener {
    val listeners = mutableListOf<ImageRequest.Listener>()

    override fun onStart(request: ImageRequest) {
        listeners.forEach { it.onStart(request) }
    }

    override fun onCancel(request: ImageRequest) {
        listeners.forEach { it.onCancel(request) }
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        listeners.forEach { it.onSuccess(request, result) }
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        listeners.forEach { it.onError(request, result) }
    }

    fun addListener(
        onStart: ((request: ImageRequest) -> Unit)? = null,
        onError: ((request: ImageRequest, result: ErrorResult) -> Unit)? = null,
        onSuccess: ((request: ImageRequest, result: SuccessResult) -> Unit)? = null
    ) {
        val listener = object : ImageRequest.Listener {
            override fun onStart(request: ImageRequest) {
                onStart?.invoke(request)
            }

            override fun onError(request: ImageRequest, result: ErrorResult) {
                onError?.invoke(request, result)
            }

            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                onSuccess?.invoke(request, result)
            }
        }
        listeners.add(listener)
    }
}