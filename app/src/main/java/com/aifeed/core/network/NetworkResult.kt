package com.aifeed.core.network

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw RuntimeException(message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    inline fun <R> map(transform: (T) -> R): NetworkResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    inline fun onSuccess(action: (T) -> Unit): NetworkResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String, Int?) -> Unit): NetworkResult<T> {
        if (this is Error) action(message, code)
        return this
    }

    companion object {
        suspend fun <T> safeApiCall(apiCall: suspend () -> retrofit2.Response<T>): NetworkResult<T> {
            return try {
                val response = apiCall()
                if (response.isSuccessful) {
                    response.body()?.let {
                        Success(it)
                    } ?: Error("Empty response body", response.code())
                } else {
                    Error(
                        response.errorBody()?.string() ?: "Unknown error",
                        response.code()
                    )
                }
            } catch (e: Exception) {
                Error(e.message ?: "Network error")
            }
        }
    }
}
