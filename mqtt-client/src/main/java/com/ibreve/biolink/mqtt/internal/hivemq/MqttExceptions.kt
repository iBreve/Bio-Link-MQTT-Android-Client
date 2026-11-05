package com.ibreve.biolink.mqtt.internal.hivemq

import com.ibreve.biolink.mqtt.MqttClientException
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun Throwable.asMqttClientException(operation: String): MqttClientException {
    if (this is MqttClientException) {
        return this
    }

    val cause = unwrapCompletionException()
    if (cause is MqttClientException) {
        return cause
    }

    val detail = cause.message ?: cause.javaClass.simpleName
    return MqttClientException(
        message = "MQTT $operation failed: $detail",
        cause = cause,
    )
}

internal suspend fun <T> CompletionStage<T>.awaitMqtt(operation: String): T {
    val future = toCompletableFuture()
    return suspendCancellableCoroutine { continuation ->
        future.whenComplete { value, throwable ->
            if (!continuation.isActive) {
                return@whenComplete
            }
            if (throwable == null) {
                continuation.resume(value)
            } else {
                continuation.resumeWithException(throwable.asMqttClientException(operation))
            }
        }
        continuation.invokeOnCancellation {
            future.cancel(true)
        }
    }
}

private fun Throwable.unwrapCompletionException(): Throwable {
    return if (this is CompletionException && cause != null) {
        cause!!
    } else {
        this
    }
}
